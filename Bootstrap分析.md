# 基本结构
其实Bootstrap包是最好懂的，因为只有6个文件。  
其中AbstractBootstrapConfig和BootstrapConfig和ServerBootstrapConfig是作为config用的。  
我们可以配置好一个Config，然后丢进Bootstrap类中。  
或者我们用Bootstrap类中的builder方法一个一个配也可以。  

Bootstrap分为AbstractBootstrap，Bootstrap和ServerBootstrap。  
因为无论是客户端还是服务端都要bind一个本地端口，所以在AbstractBootstrap中帮忙做了这个事。  
客户端的Bootstrap比较简单，基本没什么事做。  
客户端的ServerBootstrap还需要进行EventLoopGroup的管理之类的配置，所以相应的复杂。  

# 本地端口bind
本地端口的bind是AbstractBootstrap中进行的。  
我们进行配置完信息后，会调用`bootstrap.bind()`来进行启动。
他会调用`doBind()`方法，传进去的是本地的地址  
```java
private ChannelFuture doBind(final SocketAddress localAddress) {
    //initAndRegister()方法new了一个channel出来，然后进行初始化，
    //最后把他注册到eventLoopGroup中，因为是异步的，所以返回一个future。
    final ChannelFuture regFuture = initAndRegister();
    final Channel channel = regFuture.channel();
    if (regFuture.cause() != null) {
        return regFuture;
    }

    //下面这两个操作本质上其实没区别，最后都调用了doBind0()
    if (regFuture.isDone()) {
        ChannelPromise promise = channel.newPromise();
        doBind0(regFuture, channel, localAddress, promise);
        return promise;
    } else {
        final PendingRegistrationPromise promise = new PendingRegistrationPromise(channel);
        regFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                Throwable cause = future.cause();
                if (cause != null) {
                    promise.setFailure(cause);
                } else {
                    promise.registered();
                    doBind0(regFuture, channel, localAddress, promise);
                }
            }
        });
        return promise;
    }
}
```
doBind0()的操作其实也很简单，因为前面在initAndRegister()中我们已经把这个channel注册到第一个eventLoopGroup中，  
而eventLoopGroup其实可以做一个线程池的作用，于是我们在调用一个channel的bind方法。就把这个作为listen的channel和一个线程绑定了。  
```java
private static void doBind0(
    final ChannelFuture regFuture, final Channel channel,
    final SocketAddress localAddress, final ChannelPromise promise) {
    channel.eventLoop().execute(new Runnable() {
        @Override
        public void run() {
            if (regFuture.isSuccess()) {
                channel.bind(localAddress, promise).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            } else {
                promise.setFailure(regFuture.cause());
            }
        }
    });
}
```

至此，如果是客户端，其实工作已经完成了。  
但是如果是服务端，还有一个worker的EventLoopGroup未看到身影。  
但是其实也已经完成了，就在我们之前的那个initAndRegister()方法中。  

# worker-EventLoopGroup的运作
在`initAndRegister()`方法中
```java
final ChannelFuture initAndRegister() {
    Channel channel = null;
    try {
        channel = channelFactory.newChannel();
        init(channel);
    } catch (Throwable t) {
    	//...
    }
    //...
    return regFuture;
}
```
调用了`init(channel)`这个方法，这个方法是个抽象方法，各自的子类实现。  
在ServerBootstrap中的实现最后还对worker的EventLoopGroup进行了操作。  
```java
void init(Channel channel) throws Exception {
    //option的操作省略
    //Attrs的操作省略

    //得到与channel对应的pipeline
    ChannelPipeline p = channel.pipeline();

    //这个就是worker-eventLoopGroup
    final EventLoopGroup currentChildGroup = childGroup;
    final ChannelHandler currentChildHandler = childHandler;

    //这个就是核心的所在了，netty调用了channel的pipeline，增加了一个ServerBootstrapAcceptor的handler
    //并且把worker-eventLoopGroup作为参数传了进去，完成了worker-eventLoopGroup的操作。
    //所以，其实ServerBootstrap的childHandler和child-eventLoopGroup被包装成了一个ServerBootstrapAcceptor的handler
    //加到了boss channel的末尾handler。
    p.addLast(new ChannelInitializer<Channel>() {
        @Override
        public void initChannel(final Channel ch) throws Exception {
            final ChannelPipeline pipeline = ch.pipeline();
            ChannelHandler handler = config.handler();
            if (handler != null) {
                pipeline.addLast(handler);
            }

            ch.eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    pipeline.addLast(new ServerBootstrapAcceptor(
                            ch, currentChildGroup, currentChildHandler, currentChildOptions, currentChildAttrs));
                }
            });
        }
    });
}
```

# ServerBootstrapAcceptor
这个类是ServerBootstrap的内部类。它继承了ChannelInboundHandlerAdapter类。  
聪channelRead中我们可以看到，前面的负责accept的channel进行处理之后，丢给了这个handler一个连接的channel。  
ServerBootstrapAcceptor把childHandler加到了这个连接的channel的pipeline中。  
最后调用childGroup.register()方法，把连接的channel进行操作。  
那么这个childGroup的register方法进行了什么操作呢，我还没看到，对不起。。。   
```java
private static class ServerBootstrapAcceptor extends ChannelInboundHandlerAdapter {

        private final EventLoopGroup childGroup;
        private final ChannelHandler childHandler;
        private final Entry<ChannelOption<?>, Object>[] childOptions;
        private final Entry<AttributeKey<?>, Object>[] childAttrs;
        private final Runnable enableAutoReadTask;

        ServerBootstrapAcceptor(
                final Channel channel, EventLoopGroup childGroup, ChannelHandler childHandler,
                Entry<ChannelOption<?>, Object>[] childOptions, Entry<AttributeKey<?>, Object>[] childAttrs) {
            this.childGroup = childGroup;
            this.childHandler = childHandler;
            this.childOptions = childOptions;
            this.childAttrs = childAttrs;

            enableAutoReadTask = new Runnable() {
                @Override
                public void run() {
                    channel.config().setAutoRead(true);
                }
            };
        }

        @Override
        @SuppressWarnings("unchecked")
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            final Channel child = (Channel) msg;

            child.pipeline().addLast(childHandler);

            setChannelOptions(child, childOptions, logger);

            for (Entry<AttributeKey<?>, Object> e: childAttrs) {
                child.attr((AttributeKey<Object>) e.getKey()).set(e.getValue());
            }

            try {
                childGroup.register(child).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (!future.isSuccess()) {
                            forceClose(child, future.cause());
                        }
                    }
                });
            } catch (Throwable t) {
                forceClose(child, t);
            }
        }

        private static void forceClose(Channel child, Throwable t) {
            child.unsafe().closeForcibly();
            logger.warn("Failed to register an accepted channel: {}", child, t);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            final ChannelConfig config = ctx.channel().config();
            if (config.isAutoRead()) {
                config.setAutoRead(false);
                ctx.channel().eventLoop().schedule(enableAutoReadTask, 1, TimeUnit.SECONDS);
            }
            ctx.fireExceptionCaught(cause);
        }
    }

```


