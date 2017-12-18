![EventLoopGroup](media/EventLoopGroup.png)

# 基本职责
EventLoopGroup其实是对EventLoop的更高一层的抽象，不仅仅是包含了很多EventLoop的Group而已。
就像BeanFactory和ApplicationContext的一样。

# 基本架构
ThreadFactory
import io.netty.util.concurrent.DefaultThreadFactory;

FastThreadLocalThread

MultithreadEventExecutorGroup

FastThreadLocal
FastThreadLocalThread
