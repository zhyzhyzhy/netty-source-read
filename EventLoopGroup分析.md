![EventLoopGroup](media/EventLoopGroup.png)

# 基本职责
EventLoopGroup其实是对EventLoop的更高一层的抽象，不仅仅是包含了很多EventLoop的Group而已。
就像BeanFactory和ApplicationContext的一样。

它其中包含了一个EventLoop的数组，在新连接产生的channel寻找一个EventLoop进行注册时
通过它进行寻找，它则通过Choose方法对EventLoop进行调度。


# 初始化流程
我们在启动Bootstrap时指定了NioEventLoopGroup。
下面是调用链
```java
## NioEventLoopGroup ->
public NioEventLoopGroup() {
    this(0);
}
public NioEventLoopGroup(int nThreads) {
    this(nThreads, (Executor) null);
}
public NioEventLoopGroup(int nThreads, Executor executor) {
    this(nThreads, executor, SelectorProvider.provider());
}
public NioEventLoopGroup(
        int nThreads, Executor executor, final SelectorProvider selectorProvider) {
    this(nThreads, executor, selectorProvider, DefaultSelectStrategyFactory.INSTANCE);
}
public NioEventLoopGroup(int nThreads, Executor executor, final SelectorProvider selectorProvider,
                         final SelectStrategyFactory selectStrategyFactory) {
    super(nThreads, executor, selectorProvider, selectStrategyFactory, RejectedExecutionHandlers.reject());
}
```

```java
## MultithreadEventLoopGroup ->

private static final int DEFAULT_EVENT_LOOP_THREADS;
//这里的DEFAULT_EVENT_LOOP_THREADS默认的是处理器的个数，所以一个裸的EventLoopGroup的线程是处理器个数乘2
static {
    DEFAULT_EVENT_LOOP_THREADS = Math.max(1, SystemPropertyUtil.getInt(
            "io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 2));

    if (logger.isDebugEnabled()) {
        logger.debug("-Dio.netty.eventLoopThreads: {}", DEFAULT_EVENT_LOOP_THREADS);
    }
}
protected MultithreadEventLoopGroup(int nThreads, Executor executor, Object... args) {
    super(nThreads == 0 ? DEFAULT_EVENT_LOOP_THREADS : nThreads, executor, args);
}
```

```java
## MultithreadEventExecutorGroup ->
protected MultithreadEventExecutorGroup(int nThreads, ThreadFactory threadFactory, Object... args) {
    this(nThreads, threadFactory == null ? null : new ThreadPerTaskExecutor(threadFactory), args);
}
protected MultithreadEventExecutorGroup(int nThreads, Executor executor, Object... args) {
    this(nThreads, executor, DefaultEventExecutorChooserFactory.INSTANCE, args);
}
protected MultithreadEventExecutorGroup(int nThreads, Executor executor,
                                        EventExecutorChooserFactory chooserFactory, Object... args) {
    if (nThreads <= 0) {
        throw new IllegalArgumentException(String.format("nThreads: %d (expected: > 0)", nThreads));
    }

    //这里的executor因为是null， 所以默认设置的是ThreadPerTaskExecutor
    if (executor == null) {
        executor = new ThreadPerTaskExecutor(newDefaultThreadFactory());
    }

    children = new EventExecutor[nThreads];

    for (int i = 0; i < nThreads; i ++) {
        boolean success = false;
        try {
            children[i] = newChild(executor, args);
            success = true;
        } catch (Exception e) {
            // TODO: Think about if this is a good exception type
            throw new IllegalStateException("failed to create a child event loop", e);
        } finally {
            if (!success) {
                for (int j = 0; j < i; j ++) {
                    children[j].shutdownGracefully();
                }

                for (int j = 0; j < i; j ++) {
                    EventExecutor e = children[j];
                    try {
                        while (!e.isTerminated()) {
                            e.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
                        }
                    } catch (InterruptedException interrupted) {
                        // Let the caller handle the interruption.
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    chooser = chooserFactory.newChooser(children);

    final FutureListener<Object> terminationListener = new FutureListener<Object>() {
        @Override
        public void operationComplete(Future<Object> future) throws Exception {
            if (terminatedChildren.incrementAndGet() == children.length) {
                terminationFuture.setSuccess(null);
            }
        }
    };

    for (EventExecutor e: children) {
        e.terminationFuture().addListener(terminationListener);
    }

    Set<EventExecutor> childrenSet = new LinkedHashSet<EventExecutor>(children.length);
    Collections.addAll(childrenSet, children);
    readonlyChildren = Collections.unmodifiableSet(childrenSet);
}
```

# 注册调度
channel在注册到EventLoop的过程中，依靠的是EventLoopGroup进行的调度。
从Bootstrap的分析我们可以知道，在ServerBootstrapAcceptor中的read0方法中，调用了
`childGroup.register(child)`
方法。
那么调用链是啥样的呢。
```java
## MultithreadEventLoopGroup -> 
public ChannelFuture register(Channel channel) {
    return next().register(channel);
}
public EventLoop next() {
    return (EventLoop) super.next();
}
```

```java
## MultithreadEventExecutorGroup ->
@Override
public EventExecutor next() {
    return chooser.next();
}
//关于这个choose，还是参看源代码中我的注释把。。。太累了，不想再打一遍字了。。。
TODO
```



