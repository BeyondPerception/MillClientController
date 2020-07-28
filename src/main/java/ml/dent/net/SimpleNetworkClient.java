package ml.dent.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.util.CharsetUtil;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.net.ConnectException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A basic working implementation of the network client that can write data and
 * handle the bounce server initialization sequence
 *
 * @author Ronak Malik
 */
public class SimpleNetworkClient extends AbstractNetworkClient {

    private static final int BUFFER_SIZE = 256;

    private boolean proxyEnabled;
    private boolean bounceServerProtocol;
    private boolean buffering;
    private int     channel;
    private int     internalPort;

    private BooleanProperty connectionStatusProperty = new SimpleBooleanProperty(false);
    private BooleanProperty connectionAttempted      = new SimpleBooleanProperty(false);

    /**
     * @param host    The hostname to connect to
     * @param port    The server's port to connect to
     * @param channel provides the channel that this client can send and receive
     *                messages on, this client will only send and receive messages
     *                on this channel
     */
    public SimpleNetworkClient(String host, int port, int channel) {
        this(host, port, channel, false);
    }

    /**
     * @param buffer  Unless your write operations are low-frequency, not enabling buffering can
     *                severely impact performance and put unnecessary load on the CPU.
     * @param channel provides the channel that this client can send and receive
     *                messages on, this client will only send and receive messages
     *                on this channel
     */
    public SimpleNetworkClient(String host, int port, int channel, boolean buffer) {
        super(host, port);
        this.channel = channel;
        this.buffering = buffer;
        internalPort = getPort();
        proxyConnectionEstablished = new AtomicBoolean(false);
        authenticationMessage = "hi";
        bounceServerProtocol = true;
    }

    @Override
    public ChannelFuture connect() {
        return connect(new ClientOutboundHandler(), new ClientInboundHandler(), new ActiveHandler());
    }

    @Override
    public ChannelFuture connect(ChannelHandler... channelHandlers) {
        connectionAttempted.set(false);
        ChannelHandler[] newHandlers = new ChannelHandler[channelHandlers.length + 3];
        newHandlers[0] = new ClientOutboundHandler();
        newHandlers[1] = new ClientInboundHandler();
        newHandlers[2] = new ActiveHandler();
        System.arraycopy(channelHandlers, 0, newHandlers, 3, newHandlers.length - 3);

        ChannelFuture cf = super.connect(newHandlers);
        return generateNewChannelFuture(cf);
    }

    private ChannelFuture generateNewChannelFuture(ChannelFuture cf) {
        return new DefaultChannelPromise(getChannel()) {
            {
                connectionAttempted.addListener((obv, oldVal, newVal) -> {
                    if (isConnectionActive()) {
                        setSuccess();
                    } else {
                        if (proxyEnabled && !isProxyEstablished()) {
                            setFailure(new ConnectException("Failed to initiate proxy"));
                            return;
                        }
                        if (bounceServerProtocol) {
                            setFailure(new ConnectException("Failed to negotiate with bounce server"));
                        } else {
                            setFailure(new ConnectException());
                        }
                    }
                });
            }

            @Override
            public boolean isDone() {
                return connectionAttempted.get() && cf.isDone();
            }

            @Override
            public boolean isSuccess() {
                return isConnectionActive() && cf.isSuccess();
            }
        };
    }

    @Override
    public boolean isConnectionActive() {
        return connectionStatusProperty.get();
    }

    @Override
    public ReadOnlyBooleanProperty connectionActiveProperty() {
        return connectionStatusProperty;
    }

    public ChannelFuture write(String s) {
        return write(Unpooled.copiedBuffer(s, CharsetUtil.UTF_8));
    }

    private PooledByteBufAllocator alloc = new PooledByteBufAllocator();
    private ByteBuf                curBuffer;

    public void write(byte b) {
        if (buffering) {
            if (curBuffer == null) {
                curBuffer = alloc.buffer(BUFFER_SIZE, BUFFER_SIZE);
            }
            if (curBuffer.writableBytes() <= 0) {
                write(curBuffer);
                curBuffer = alloc.buffer(BUFFER_SIZE, BUFFER_SIZE);
            }
            curBuffer.writeByte(b);
        } else {
            ByteBuf buf = alloc.buffer();
            buf.writeByte(b);
            write(buf);
        }
    }

    public ChannelFuture write(Object o) {
        if (!isConnectionActive()) {
            throw new IllegalStateException("Cannot write to non-active Channel!");
        }

        if (proxyEnabled() && !isProxyEstablished()) {
            throw new IllegalStateException("Proxy enabled but not yet established!");
        }

        return getChannel().write(o);
    }

    public void flush() {
        getChannel().flush();
    }

    public ChannelFuture writeAndFlush(String s) {
        ChannelFuture cf = write(s);
        flush();
        return cf;
    }

    public void writeAndFlush(byte b) {
        write(b);
        flush();
    }

    public ChannelFuture writeAndFlush(Object o) {
        ChannelFuture cf = write(o);
        flush();
        return cf;
    }

    public int getInternalPort() {
        return internalPort;
    }

    public void setInternalPort(int newPort) {
        internalPort = newPort;
    }

    public void enableProxy(boolean set) {
        proxyEnabled = set;
    }

    public boolean proxyEnabled() {
        return proxyEnabled;
    }

    public void setBounceServerProtocol(boolean bounceServerProtocol) {
        this.bounceServerProtocol = bounceServerProtocol;
    }

    public boolean getBounceServerProtocol() {
        return bounceServerProtocol;
    }

    private boolean proxyAttempted;

    /**
     * Will return true if the proxy is not enabled, otherwise will return if the
     * proxy has been attempted
     */
    public boolean proxyConnectionAttempted() {
        if (!proxyEnabled()) {
            return true;
        }

        return proxyAttempted;
    }

    public boolean isProxyEstablished() {
        return proxyConnectionEstablished.get();
    }

    private String authenticationMessage;

    public String getAuthenticationMessage() {
        return authenticationMessage;
    }

    public void setAuthenticationMessage(String s) {
        authenticationMessage = s;
    }

    /**
     * This method should be used over the the super classes isConnectionActive when
     * checking if the connection is ready to write to as it will run additional
     * checks as to whether this connection is ready to use
     *
     * @return Whether the connection is not only active, but established and ready
     * to use to the knowledge of this class
     */
    public boolean isConnectionReady() {
        if (!isConnectionActive()) {
            return false;
        }

        return !proxyEnabled() || isProxyEstablished();
    }

    private AtomicBoolean proxyConnectionEstablished;

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String newName) {
        name = newName;
    }

    private class ClientInboundHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            if (proxyEnabled) {
                String httpReq = "CONNECT localhost:" + getInternalPort() + " HTTP/1.1\r\n" + "Host: localhost:1111\r\n"
                        + "Proxy-Connection: Keep-Alive\r\n" + "\r\n";

                ctx.writeAndFlush(Unpooled.copiedBuffer(httpReq, CharsetUtil.UTF_8));
            } else if (!bounceServerProtocol) {
                super.channelActive(ctx);
            }
        }

        private AtomicBoolean verStringRecv = new AtomicBoolean(false);

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            // proxy happens before bounce server so we check that first
            if (proxyEnabled() && !proxyConnectionEstablished.get()) {
                String estConfirm = ((ByteBuf) msg).toString(CharsetUtil.UTF_8);
                if (checkEstablished(estConfirm)) {
                    super.channelActive(ctx);
                }
                proxyAttempted = true;
            } else if (bounceServerProtocol) {
                if (!verStringRecv.get()) {
                    try {
                        String verString = ((ByteBuf) msg).toString(CharsetUtil.UTF_8);
                        String channelBytesStr = verString.substring(0, verString.indexOf("-"));
                        int channelBytes = Integer.parseInt(channelBytesStr);
                        // We use ctx.writeAndFlush instead of our own write method because we don't
                        // want the message traveling through the entire pipeline
                        if (authenticationMessage != null) {
                            ctx.writeAndFlush(Unpooled.copiedBuffer(authenticationMessage, CharsetUtil.UTF_8));
                        }
                        if (channel != -1) {
                            ctx.writeAndFlush(Unpooled.copiedBuffer(String.format("%0" + channelBytes + "x", channel), CharsetUtil.UTF_8));
                        }
                        verStringRecv.set(true);
                        super.channelActive(ctx);
                    } finally {
                        connectionAttempted.set(true);
                    }
                } else {
                    super.channelRead(ctx, msg);
                }
            } else {
                super.channelRead(ctx, msg);
            }
        }

        private boolean checkEstablished(String confirm) {
            return confirm.contains("Established");
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            super.exceptionCaught(ctx, cause);
        }
    }

    // This class simulates a handler that would be after the normal SimpleNetworkClient handler in the pipeline
    // to detect when this channel is ready to be used
    private class ActiveHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            if (proxyEnabled) {
                proxyConnectionEstablished.set(true);
            }
            connectionAttempted.set(true);
            connectionStatusProperty.set(true);
            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            connectionAttempted.set(true);
            connectionStatusProperty.set(false);
            super.channelInactive(ctx);
        }
    }

    private static class ClientOutboundHandler extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            super.write(ctx, msg, promise);
        }
    }
}
