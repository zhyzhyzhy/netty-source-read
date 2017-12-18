Channel这一块不想Bootstrap那么简介明了
这里包含了Channel，ChannelPipeLine，Future，Promise，Context这些东西。同时还包含了Util包的一些东西。
非常的复杂。
为了简化，我主要看NioServerSocketChannel。

![NioServerSocketChannel](media/NioServerSocketChannel.png)

# 整体架构
netty进行了几个抽象。

## EventLoop
EventLoop其实是对一个线程的封装，netty把连接的channel给eventLoop
然后每个eventLoop可以同时操作很多的channel，每个channel从生到死都在一个eventLoop中，这就避免了多线程的锁操作。
提高了很多的效率。

# EventLoop
![EventLoop](media/NioEventLoop.png)
一般我们是直接生成NioEventLoopGroup，在NioEventLoopGroup中有一个工厂方法来得到NioEventLoop。
在MultithreadEventExecutorGroup类中定义了这个方法
```java
protected abstract EventExecutor newChild(Executor executor, Object... args) throws Exception;
```
在NioEventLoopGroup中的实现是这样
```java
@Override
protected EventLoop newChild(Executor executor, Object... args) throws Exception {
    return new NioEventLoop(this, executor, (SelectorProvider) args[0],
        ((SelectStrategyFactory) args[1]).newSelectStrategy(), (RejectedExecutionHandler) args[2]);
}
```





# new方法到底发生了啥
在Bootstrap中，我们在设置channel时，传进去了一个class对象
于是，在里面生成了一个ChannelFactory的工厂对象。
```java
public class ReflectiveChannelFactory<T extends Channel> implements ChannelFactory<T> {

    private final Class<? extends T> clazz;

    public ReflectiveChannelFactory(Class<? extends T> clazz) {
        if (clazz == null) {
            throw new NullPointerException("clazz");
        }
        this.clazz = clazz;
    }

    @Override
    public T newChannel() {
        try {
            return clazz.getConstructor().newInstance();
        } catch (Throwable t) {
            throw new ChannelException("Unable to create Channel from class " + clazz, t);
        }
    }

}
```
我们调用newChannel的时候其实是得到了一个默认的构造方法直接newInstance了。
在NioServerSocketChannel中
```java
private static final SelectorProvider DEFAULT_SELECTOR_PROVIDER = SelectorProvider.provider();
private static ServerSocketChannel newSocket(SelectorProvider provider) {
    try {
        return provider.openServerSocketChannel();
    } catch (IOException e) {
        throw new ChannelException(
                "Failed to open a server socket.", e);
    }
}
public NioServerSocketChannel() {
    this(newSocket(DEFAULT_SELECTOR_PROVIDER));
}
public NioServerSocketChannel(ServerSocketChannel channel) {
    super(null, channel, SelectionKey.OP_ACCEPT);
    config = new NioServerSocketChannelConfig(this, javaChannel().socket());
}
```

AbstractNioMessageChannel中
```java
protected AbstractNioMessageChannel(Channel parent, SelectableChannel ch, int readInterestOp) {
    super(parent, ch, readInterestOp);
}
```
AbstractNioChannel中
```java
protected AbstractNioChannel(Channel parent, SelectableChannel ch, int readInterestOp) {
    super(parent);
    this.ch = ch;
    this.readInterestOp = readInterestOp;
    try {
        ch.configureBlocking(false);
    } catch (IOException e) {
        try {
            ch.close();
        } catch (IOException e2) {
            if (logger.isWarnEnabled()) {
                logger.warn(
                        "Failed to close a partially initialized socket.", e2);
            }
        }

        throw new ChannelException("Failed to enter non-blocking mode.", e);
    }
}
```
AbstractChannel中
```java
protected AbstractChannel(Channel parent) {
    this.parent = parent;
    id = newId();
    unsafe = newUnsafe();
    pipeline = newChannelPipeline();
}
```

所以，整个流程是这样的
* ChannelFactory调用newChannel，调用的是ServerSocketChannel的默认构造方法
* ServerSocketChannel调用newSocket，创建一个新的ServerSocketChannel。
* AbstractChannel设置parent channel，给channel创建一个唯一的id，一个pipeline和一个unsafe实例
* AbstractNioChannel设置SelectionKey，服务端的是ON_ACCEPT，同时设置channel为非阻塞。
* ServerSocketChannel新建一个NioServerSocketChannelConfig，同时把SelectionKey.OP_ACCEPT传给父构造器






# EventLoopGroup和EventLoop
我们在启动Bootstrap时指定了NioEventLoopGroup。
下面是调用链
```java
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
MultithreadEventLoopGroup

```java
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
MultithreadEventExecutorGroup
```java
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
