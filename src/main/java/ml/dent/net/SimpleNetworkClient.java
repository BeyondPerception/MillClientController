package ml.dent.net;

import java.util.concurrent.atomic.AtomicBoolean;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.CharsetUtil;

/**
 * 
 * A basic working implementation of the network client that can write data and
 * handle the bounce server initialization sequence
 * 
 * @author Ronak Malik
 */
public class SimpleNetworkClient extends AbstractNetworkClient {

	private char	channel;

	private int		internalPort;

	private boolean	proxyEnabled;

	/**
	 * @param channel provides the channel that this client can send and receive
	 *                messages on, this client will only send and receive messages
	 *                on this channel
	 */
	public SimpleNetworkClient(String host, int port, char channel) {
		super(host, port);
		this.channel = channel;
		internalPort = getPort();
		proxyConnectionEstablished = new AtomicBoolean(false);
		authenticationMessage = "hi";
	}

	@Override
	public ChannelFuture connect() {
		return super.connect(new ClientOutboundHandler(), new ClientInboundHandler());
	}

	@Override
	public ChannelFuture connect(ChannelHandler... channelHandlers) {
		ChannelHandler[] newHandlers = new ChannelHandler[channelHandlers.length + 2];
		newHandlers[0] = new ClientOutboundHandler();
		newHandlers[1] = new ClientInboundHandler();
		for (int i = 2; i < newHandlers.length; i++) {
			newHandlers[i] = channelHandlers[i - 2];
		}

		return super.connect(newHandlers);
	}

	public ChannelFuture write(String s) {
		return write(Unpooled.copiedBuffer(s, CharsetUtil.UTF_8));
	}

	public ChannelFuture write(byte b) {
		ByteBuf buf = Unpooled.buffer(0x1);
		buf.writeByte(b);
		return write(buf);
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

	public Channel flush() {
		return getChannel().flush();
	}

	public ChannelFuture writeAndFlush(String s) {
		ChannelFuture cf = write(s);
		flush();
		return cf;
	}

	public ChannelFuture writeAndFlush(byte b) {
		ChannelFuture cf = write(b);
		flush();
		return cf;
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
	 * 
	 * This method should be used over the the super classes isConnectionActive when
	 * checking if the connection is ready to write to as it will run additional
	 * checks as to whether this connection is ready to use
	 * 
	 * @return Whether the connection is not only active, but established and ready
	 *         to use to the knowledge of this class
	 */
	public boolean isConnectionReady() {
		if (!isConnectionActive()) {
			return false;
		}

		if (proxyEnabled() && !isProxyEstablished()) {
			return false;
		}

		return true;
	}

	private AtomicBoolean proxyConnectionEstablished;

	private class ClientInboundHandler extends ChannelInboundHandlerAdapter {

		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			if (proxyEnabled) {
				String httpReq = "CONNECT localhost:" + getInternalPort() + " HTTP/1.1\r\n" + "Host: localhost:1111\r\n"
						+ "Proxy-Connection: Keep-Alive\r\n" + "\r\n";

				ctx.writeAndFlush(Unpooled.copiedBuffer(httpReq, CharsetUtil.UTF_8));
			} else {
				if (authenticationMessage != null) {
					ctx.writeAndFlush(Unpooled.copiedBuffer(authenticationMessage, CharsetUtil.UTF_8));
				}
				if (channel != '-') {
					ctx.writeAndFlush(Unpooled.copiedBuffer(String.valueOf(channel), CharsetUtil.UTF_8));
				}
			}
			super.channelActive(ctx);
		}

		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
			if (proxyEnabled() && !proxyConnectionEstablished.get()) {
				String estConfirm = ((ByteBuf) msg).toString(CharsetUtil.UTF_8);
				if (checkEstablished(estConfirm)) {
					proxyConnectionEstablished.set(true);

					// We use ctx.writeAndFlush instead of our own write method because we don't
					// want the message traveling through the entire pipeline
					if (authenticationMessage != null) {
						ctx.writeAndFlush(Unpooled.copiedBuffer(authenticationMessage, CharsetUtil.UTF_8));
					}
					if (channel != '-') {
						ctx.writeAndFlush(Unpooled.copiedBuffer(Character.toString(channel), CharsetUtil.UTF_8));
					}
				}
			} else {
				super.channelRead(ctx, msg);
			}
			if (proxyEnabled()) {
				proxyAttempted = true;
			}
		}

		private boolean checkEstablished(String confirm) {
			if (confirm.contains("Established")) {
				return true;
			}
			return false;
		}
	}

	private class ClientOutboundHandler extends ChannelOutboundHandlerAdapter {
		@Override
		public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
			super.write(ctx, msg, promise);
		}

	}
}
