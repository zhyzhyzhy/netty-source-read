# 前言
Netty的Thread不是直接的系统的Thread类，而是用自己的FastThreadLocalThread
```java
public class FastThreadLocalThread extends Thread {

}
```
从名字我们就可以看出来这个类改进了ThreadLocal，并且更快。
因为Thread中包含了一个ThreadLocalMap的成员变量，所以要进行修改自然要从这里下手。

# DefaultThreadFactory
```java
public class DefaultThreadFactory implements ThreadFactory {
	//这个poolId，大概就是DefaultThreadFactory的id吧，因为DefaultThreadFactory不是单例的。
    private static final AtomicInteger poolId = new AtomicInteger();
    //当前ThreadFactory中的线程id生成器
    private final AtomicInteger nextId = new AtomicInteger();
    //前缀，为了给线程规范名字用
    private final String prefix;
    //是否是守护线程
    private final boolean daemon;
    //线程的优先级
    private final int priority;

    protected final ThreadGroup threadGroup;


    @Override
    public Thread newThread(Runnable r) {
        Thread t = newThread(FastThreadLocalRunnable.wrap(r), prefix + nextId.incrementAndGet());
        try {
            if (t.isDaemon() != daemon) {
                t.setDaemon(daemon);
            }

            if (t.getPriority() != priority) {
                t.setPriority(priority);
            }
        } catch (Exception ignored) {
            // Doesn't matter even if failed to set.
        }
        return t;
    }

    protected Thread newThread(Runnable r, String name) {
        return new FastThreadLocalThread(threadGroup, r, name);
    }

}
```
其中，这个FastThreadLocalRunnable类很简单，重载了run方法，在run执行之后还调用了FastThreadLocal.removeAll()
```java
final class FastThreadLocalRunnable implements Runnable {
    private final Runnable runnable;

    private FastThreadLocalRunnable(Runnable runnable) {
        this.runnable = ObjectUtil.checkNotNull(runnable, "runnable");
    } 

    @Override
    //这个相当于重载了Runnable了，它的run方法，最后的finally中还增加了FastThreadLocal.removeAll();这句话。
    public void run() {
        try {
            runnable.run();
        } finally {
            FastThreadLocal.removeAll();
        }
    }

    static Runnable wrap(Runnable runnable) {
        return runnable instanceof FastThreadLocalRunnable ? runnable : new FastThreadLocalRunnable(runnable);
    }
}
```

# FastThreadLocalMap
这个类是Netty的实现，我们知道在系统的ThreadMap中，key是当前ThreadLocal的弱引用。
并且处理冲突的方法是使用线性探测法，就是说，如果一次不命中，那么就继续往下找，如果遇到key为null的，还要进行清除工作。
这样最坏的情况可能恶化到O(n)。
那么Netty的InternalThreadLocalMap就不是使用的线性探测法。

netty其实还包含了系统的ThreadLocalMap，不过叫它slowThreadLocal，而自己的叫fastThreadLocal
```java
class UnpaddedInternalThreadLocalMap {
    //这个是系统的ThreadLocalMap，在这里叫slowThreadLocalMap
    static final ThreadLocal<InternalThreadLocalMap> slowThreadLocalMap = new ThreadLocal<InternalThreadLocalMap>();
    static final AtomicInteger nextIndex = new AtomicInteger();

    /** Used by {@link FastThreadLocal} */
    //给FastThreadLocal用的，每个Thread一个
    Object[] indexedVariables;

    // Core thread-locals
    int futureListenerStackDepth;
    int localChannelReaderStackDepth;
    Map<Class<?>, Boolean> handlerSharableCache;
    IntegerHolder counterHashCode;
    ThreadLocalRandom random;
    Map<Class<?>, TypeParameterMatcher> typeParameterMatcherGetCache;
    Map<Class<?>, Map<String, TypeParameterMatcher>> typeParameterMatcherFindCache;

    // String-related thread-locals
    StringBuilder stringBuilder;
    Map<Charset, CharsetEncoder> charsetEncoderCache;
    Map<Charset, CharsetDecoder> charsetDecoderCache;

    // ArrayList-related thread-locals
    ArrayList<Object> arrayList;

    UnpaddedInternalThreadLocalMap(Object[] indexedVariables) {
        this.indexedVariables = indexedVariables;
    }
}
```

# FastThreadLocalThread
这个类其实并不复杂，主要就是多了两个成员变量
```java
private final boolean cleanupFastThreadLocals;

private InternalThreadLocalMap threadLocalMap;
```
第一个cleanupFastThreadLocals标记是否要清除当前线程的ThreadLocals
第二个就是我们上面提到的重载的ThreadLocals。

# FastThreadLocal
首先构造函数得到一个全局的id
```java
private final int index;
public FastThreadLocal() {
    //每一个FastThreadLocal都有一个唯一的index，这个index标志着这个threadLocal在每个线程的ThreadLocal数组中的位置
    index = InternalThreadLocalMap.nextVariableIndex();
}

static final AtomicInteger nextIndex = new AtomicInteger();
public static int nextVariableIndex() {
    int index = nextIndex.getAndIncrement();
    if (index < 0) {
        nextIndex.decrementAndGet();
        throw new IllegalStateException("too many thread-local indexed variables");
    }
    return index;
}
```

如果我们调用get方法，就是通过每个ThreadLocal的全局id，在每个Thread中的Map中找到自己对应的那一个。
```java
public final V get() {
    return get(InternalThreadLocalMap.get());
}
public static InternalThreadLocalMap get() {
    Thread thread = Thread.currentThread();
    if (thread instanceof FastThreadLocalThread) {
        return fastGet((FastThreadLocalThread) thread);
    } else {
        return slowGet();
    }
}
private static InternalThreadLocalMap fastGet(FastThreadLocalThread thread) {
    InternalThreadLocalMap threadLocalMap = thread.threadLocalMap();
    if (threadLocalMap == null) {
        thread.setThreadLocalMap(threadLocalMap = new InternalThreadLocalMap());
    }
    return threadLocalMap;
}
public final V get(InternalThreadLocalMap threadLocalMap) {
    Object v = threadLocalMap.indexedVariable(index);
    if (v != InternalThreadLocalMap.UNSET) {
        return (V) v;
    }
    return initialize(threadLocalMap);
}
```

看看set方法
```java
public final void set(V value) {
    if (value != InternalThreadLocalMap.UNSET) {
        set(InternalThreadLocalMap.get(), value);
    } else {
        remove();
    }
}
public static InternalThreadLocalMap get() {
    Thread thread = Thread.currentThread();
    if (thread instanceof FastThreadLocalThread) {
        return fastGet((FastThreadLocalThread) thread);
    } else {
        return slowGet();
    }
}
public final void set(InternalThreadLocalMap threadLocalMap, V value) {
    if (value != InternalThreadLocalMap.UNSET) {
        if (threadLocalMap.setIndexedVariable(index, value)) {
            addToVariablesToRemove(threadLocalMap, this);
        }
    } else {
        remove(threadLocalMap);
    }
}
public boolean setIndexedVariable(int index, Object value) {
    Object[] lookup = indexedVariables;
    if (index < lookup.length) {
        Object oldValue = lookup[index];
        lookup[index] = value;
        return oldValue == UNSET;
    } else {
        expandIndexedVariableTableAndSet(index, value);
        return true;
    }
}
```
大部分还是清晰明了的，但是有个函数一直让我很困惑。
addToVariablesToRemove这个函数
```java
//这个index，在FastThreadLocal中是静态的变量，虽然是通过的函数调用，但是值只要类被加载，就是固定的。
private static final int variablesToRemoveIndex = InternalThreadLocalMap.nextVariableIndex();
//那么这个方法，大概就是把所有的数组中含有的FastThreadLocal都放进去了，这个variablesToRemoveIndex位的对象被初始化为一个Set
private static void addToVariablesToRemove(InternalThreadLocalMap threadLocalMap, FastThreadLocal<?> variable) {
	Object v = threadLocalMap.indexedVariable(variablesToRemoveIndex);
	Set<FastThreadLocal<?>> variablesToRemove;
	if (v == InternalThreadLocalMap.UNSET || v == null) {
	    variablesToRemove = Collections.newSetFromMap(new IdentityHashMap<FastThreadLocal<?>, Boolean>());
	    threadLocalMap.setIndexedVariable(variablesToRemoveIndex, variablesToRemove);
	} else {
	    variablesToRemove = (Set<FastThreadLocal<?>>) v;
	}

	variablesToRemove.add(variable);
}
```

那么这个时候，我们再回头看FastThreadLocalRunnable中重写的run方法
```java
public void run() {
    try {
        runnable.run();
    } finally {
        FastThreadLocal.removeAll();
    }
}
//大概就是把当前线程中的那个ThreadLocalMap中的所有ThreadLocal都从Thread的底层数组中移除了
//这个的意义是啥呢，注释里原文是这么写的
// This operation is useful when you
// are in a container environment, and you don't want to leave the thread local variables in the threads you do not
// manage.
//嗯。。。大概就是这样。
//为什么用set而不是遍历数组呢，可能这样会快点吧。
public static void removeAll() {
    //得到当前Thread的InternalThreadLocalMap
    InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.getIfSet();
    if (threadLocalMap == null) {
        return;
    }

    try {
        //得到variablesToRemoveIndex对应的object
        Object v = threadLocalMap.indexedVariable(variablesToRemoveIndex);
        if (v != null && v != InternalThreadLocalMap.UNSET) {
            @SuppressWarnings("unchecked")
            //转化为set
            Set<FastThreadLocal<?>> variablesToRemove = (Set<FastThreadLocal<?>>) v;
            //这个set中的每一个对应着一个FastThreadLocal
            FastThreadLocal<?>[] variablesToRemoveArray =
                    variablesToRemove.toArray(new FastThreadLocal[variablesToRemove.size()]);
            //从其中删除
            for (FastThreadLocal<?> tlv: variablesToRemoveArray) {
                tlv.remove(threadLocalMap);
            }
        }
    } finally {
        InternalThreadLocalMap.remove();
    }
}
```

# 总结
FastThreadLocal为每个FastThreadLocal分配了一个全局的id，这样在Thread的底层数组中寻找的时候可能保证是O(1)的复杂度。
但是缺点就是，在程序中有多少个ThreadLocal，就要开辟多大的数组。有点浪费空间了。
总体上就是空间换时间的做法。


