package io.netty.bootstrap;

//给AbstractBootstrap的配置文件
public abstract class AbstractBootstrapConfig<B extends AbstractBootstrap<B, C>, C extends Channel> {

    protected final B bootstrap;

    protected AbstractBootstrapConfig(B bootstrap) {
        this.bootstrap = ObjectUtil.checkNotNull(bootstrap, "bootstrap");
    }

    //bootstrap的addr
    public final SocketAddress localAddress() {
        return bootstrap.localAddress();
    }

    //返回给bootstrap的channelFactory
    @SuppressWarnings("deprecation")
    public final ChannelFactory<? extends C> channelFactory() {
        return bootstrap.channelFactory();
    }

    //返回bootstrap的handler
    //对于客户端而言，就是唯一的一个handler
    //对于服务端而言，是接收channel的那个handler
    public final ChannelHandler handler() {
        return bootstrap.handler();
    }

    /**
     * Returns a copy of the configured options.
     */
    public final Map<ChannelOption<?>, Object> options() {
        return bootstrap.options();
    }

    /**
     * Returns a copy of the configured attributes.
     */
    public final Map<AttributeKey<?>, Object> attrs() {
        return bootstrap.attrs();
    }

    /**
     * Returns the configured {@link EventLoopGroup} or {@code null} if non is configured yet.
     */
    @SuppressWarnings("deprecation")
    public final EventLoopGroup group() {
        return bootstrap.group();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder()
                .append(StringUtil.simpleClassName(this))
                .append('(');
        EventLoopGroup group = group();
        if (group != null) {
            buf.append("group: ")
                    .append(StringUtil.simpleClassName(group))
                    .append(", ");
        }
        @SuppressWarnings("deprecation")
        ChannelFactory<? extends C> factory = channelFactory();
        if (factory != null) {
            buf.append("channelFactory: ")
                    .append(factory)
                    .append(", ");
        }
        SocketAddress localAddress = localAddress();
        if (localAddress != null) {
            buf.append("localAddress: ")
                    .append(localAddress)
                    .append(", ");
        }

        Map<ChannelOption<?>, Object> options = options();
        if (!options.isEmpty()) {
            buf.append("options: ")
                    .append(options)
                    .append(", ");
        }
        Map<AttributeKey<?>, Object> attrs = attrs();
        if (!attrs.isEmpty()) {
            buf.append("attrs: ")
                    .append(attrs)
                    .append(", ");
        }
        ChannelHandler handler = handler();
        if (handler != null) {
            buf.append("handler: ")
                    .append(handler)
                    .append(", ");
        }
        if (buf.charAt(buf.length() - 1) == '(') {
            buf.append(')');
        } else {
            buf.setCharAt(buf.length() - 2, ')');
            buf.setLength(buf.length() - 1);
        }
        return buf.toString();
    }
}
