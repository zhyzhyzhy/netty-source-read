# 前言
Unsafe是个藏得很深的类， = =
反正我是折腾了好久才发现这个类。

不同于sun的那个unsafe，这个unsafe也是为netty提供了很多核心的功能。
比如client的channel进来时，经由的就是unsafe的register方法。

那么为什么这个类要设为Unsafe呢。。。

# 继承
这个接口的第一次定义是和channel一起的。。。
由此看出这个类和channel的密不可分的关系
```java
//从前面的注释中看到了为什么要设为Unsafe
/**
 * <em>Unsafe</em> operations that should <em>never</em> be called from user-code. These methods
 * are only provided to implement the actual transport, and must be invoked from an I/O thread except for the
 * following methods:
 * <ul>
 *   <li>{@link #localAddress()}</li>
 *   <li>{@link #remoteAddress()}</li>
 *   <li>{@link #closeForcibly()}</li>
 *   <li>{@link #register(EventLoop, ChannelPromise)}</li>
 *   <li>{@link #deregister(ChannelPromise)}</li>
 *   <li>{@link #voidPromise()}</li>
 * </ul>
 */
interface Unsafe {

    /**
     * Return the assigned {@link RecvByteBufAllocator.Handle} which will be used to allocate {@link ByteBuf}'s when
     * receiving data.
     */
    RecvByteBufAllocator.Handle recvBufAllocHandle();

    /**
     * Return the {@link SocketAddress} to which is bound local or
     * {@code null} if none.
     */
    SocketAddress localAddress();

    /**
     * Return the {@link SocketAddress} to which is bound remote or
     * {@code null} if none is bound yet.
     */
    SocketAddress remoteAddress();

    /**
     * Register the {@link Channel} of the {@link ChannelPromise} and notify
     * the {@link ChannelFuture} once the registration was complete.
     */
    void register(EventLoop eventLoop, ChannelPromise promise);

    /**
     * Bind the {@link SocketAddress} to the {@link Channel} of the {@link ChannelPromise} and notify
     * it once its done.
     */
    void bind(SocketAddress localAddress, ChannelPromise promise);

    /**
     * Connect the {@link Channel} of the given {@link ChannelFuture} with the given remote {@link SocketAddress}.
     * If a specific local {@link SocketAddress} should be used it need to be given as argument. Otherwise just
     * pass {@code null} to it.
     *
     * The {@link ChannelPromise} will get notified once the connect operation was complete.
     */
    void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise);

    /**
     * Disconnect the {@link Channel} of the {@link ChannelFuture} and notify the {@link ChannelPromise} once the
     * operation was complete.
     */
    void disconnect(ChannelPromise promise);

    /**
     * Close the {@link Channel} of the {@link ChannelPromise} and notify the {@link ChannelPromise} once the
     * operation was complete.
     */
    void close(ChannelPromise promise);

    /**
     * Closes the {@link Channel} immediately without firing any events.  Probably only useful
     * when registration attempt failed.
     */
    void closeForcibly();

    /**
     * Deregister the {@link Channel} of the {@link ChannelPromise} from {@link EventLoop} and notify the
     * {@link ChannelPromise} once the operation was complete.
     */
    void deregister(ChannelPromise promise);

    /**
     * Schedules a read operation that fills the inbound buffer of the first {@link ChannelInboundHandler} in the
     * {@link ChannelPipeline}.  If there's already a pending read operation, this method does nothing.
     */
    void beginRead();

    /**
     * Schedules a write operation.
     */
    void write(Object msg, ChannelPromise promise);

    /**
     * Flush out all write operations scheduled via {@link #write(Object, ChannelPromise)}.
     */
    void flush();
    ChannelPromise voidPromise();

    ChannelOutboundBuffer outboundBuffer();
}
``` 