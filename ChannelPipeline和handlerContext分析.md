## 前言
ChannelPipeline是每个channel都有的，
pipeline中维护着一个handlerContext组成的双向链表。
每个handlerContext中包含着一个handler
handler可以是共享的。但是handlerContext是每个channel产生时，给每个handler都新生成了一个。
不过这个也是没有办法的，如果handlerContext共享的话，那么next和pre就搞不起来了。

偷个图= =
估计原作者也看不到
![context](https://sfault-image.b0.upaiyun.com/236/319/2363192013-5812cc600b5bf)
## 初始化
```java
public abstract class AbstractChannel extends DefaultAttributeMap implements Channel {
    private final DefaultChannelPipeline pipeline;
    protected AbstractChannel(Channel parent) {
        this.parent = parent;
        id = newId();
        unsafe = newUnsafe();
        pipeline = newChannelPipeline();
    }
    protected DefaultChannelPipeline newChannelPipeline() {
        return new DefaultChannelPipeline(this);
    }
```
可以看到，pipeline和id还有unsafe一起初始化的。


```java
public class DefaultChannelPipeline implements ChannelPipeline {
    private final Channel channel;
    private final ChannelFuture succeededFuture;
    private final VoidChannelPromise voidPromise;
    final AbstractChannelHandlerContext head;
    final AbstractChannelHandlerContext tail;
    protected DefaultChannelPipeline(Channel channel) {
        this.channel = ObjectUtil.checkNotNull(channel, "channel");
        succeededFuture = new SucceededChannelFuture(channel, null);
        voidPromise =  new VoidChannelPromise(channel, true);

        //尾handlerContext
        tail = new TailContext(this);
        //头handlerContext
        head = new HeadContext(this);

        head.next = tail;
        tail.prev = head;
    }
```

下面就是喜闻乐见的addFirst，addLast之类的操作了。

## pipeline链表操作
addFirst操作，addLast和这个差不多。
```java
@Override
public final ChannelPipeline addFirst(ChannelHandler... handlers) {
    return addFirst(null, handlers);
}

@Override
public final ChannelPipeline addFirst(EventExecutorGroup executor, ChannelHandler... handlers) {
    if (handlers == null) {
        throw new NullPointerException("handlers");
    }
    if (handlers.length == 0 || handlers[0] == null) {
        return this;
    }

    int size;
    for (size = 1; size < handlers.length; size ++) {
        if (handlers[size] == null) {
            break;
        }
    }

    for (int i = size - 1; i >= 0; i --) {
        ChannelHandler h = handlers[i];
        addFirst(executor, null, h);
    }

    return this;
}
@Override
public final ChannelPipeline addFirst(EventExecutorGroup group, String name, ChannelHandler handler) {
    final AbstractChannelHandlerContext newCtx;
    synchronized (this) {
        checkMultiplicity(handler);
        name = filterName(name, handler);
        //给这个handler new一个DefaultChannelHandlerContext
        newCtx = newContext(group, name, handler);

        addFirst0(newCtx);

        // If the registered is false it means that the channel was not registered on an eventloop yet.
        // In this case we add the context to the pipeline and add a task that will call
        // ChannelHandler.handlerAdded(...) once the channel is registered.
        if (!registered) {
            newCtx.setAddPending();
            callHandlerCallbackLater(newCtx, true);
            return this;
        }

        EventExecutor executor = newCtx.executor();
        if (!executor.inEventLoop()) {
            newCtx.setAddPending();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    callHandlerAdded0(newCtx);
                }
            });
            return this;
        }
    }
    callHandlerAdded0(newCtx);
    return this;
}
private AbstractChannelHandlerContext newContext(EventExecutorGroup group, String name, ChannelHandler handler) {
        return new DefaultChannelHandlerContext(this, childExecutor(group), name, handler);
}
private void addFirst0(AbstractChannelHandlerContext newCtx) {
    AbstractChannelHandlerContext nextCtx = head.next;
    newCtx.prev = head;
    newCtx.next = nextCtx;
    head.next = newCtx;
    nextCtx.prev = newCtx;
}
```