package ml.dent.net;

import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Objects;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;

/**
 * 
 * The base network client class that handles connecting to a server.
 * 
 * @author Ronak Malik
 */
public abstract class AbstractNetworkClient {

	private String			host;
	private int				port;

	private EventLoopGroup	group;
	private Channel			channel;
	private ChannelFuture	connectionFuture;

	private boolean			connectCalled;
	private boolean			disconnectCalled;

	public AbstractNetworkClient(String host, int port) {
		this.host = host;
		this.port = port;
	}

	/**
	 * Runs {@link #connect(ChannelHandler...)} with empty {@link ChannelHandler}s
	 */
	public ChannelFuture connect() {
		return connect(new ChannelInboundHandlerAdapter(), new ChannelOutboundHandlerAdapter());
	}

	/**
	 * Attempts to create a connection to the specified host and port
	 * 
	 * @param channelHandlers a list of {@link ChannelHandler}s that dictate how
	 *                        this connection will handle the receiving and sending
	 *                        of data. The handlers will be added in the order they
	 *                        appear in the list
	 * 
	 * @return a {@link ChannelFuture} that will be notified when the connection is
	 *         complete
	 */
	public ChannelFuture connect(ChannelHandler... channelHandlers) {
		connectCalled = true;
		disconnectCalled = false;

		group = new NioEventLoopGroup();
		Bootstrap bootstrap = new Bootstrap();

		bootstrap.group(group);
		bootstrap.channel(NioSocketChannel.class);
		bootstrap.remoteAddress(new InetSocketAddress(host, port));

		bootstrap.handler(new ChannelInitializer<SocketChannel>() {
			protected void initChannel(SocketChannel socketChannel) throws Exception {
				if (enableSSL) {
					TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
						public X509Certificate[] getAcceptedIssuers() {
							return new X509Certificate[0];
						}

						public void checkClientTrusted(X509Certificate[] certs, String authType) {
						}

						public void checkServerTrusted(X509Certificate[] certs, String authType) {
						}
					} };

					SSLContext sc = SSLContext.getInstance("SSL");
					sc.init(null, trustAllCerts, new SecureRandom());
					HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
					HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

					final SSLEngine sslEngine = sc.createSSLEngine(host, port);
					sslEngine.setUseClientMode(true);
					sslEngine.setNeedClientAuth(false);

					socketChannel.pipeline().addLast("ssl", new SslHandler(sslEngine));
				}
				socketChannel.pipeline().addLast(channelHandlers);
			}
		}).option(ChannelOption.TCP_NODELAY, true);

		connectionFuture = bootstrap.connect();
		connectionFuture.addListener(future -> {
			connectCalled = false;
		});

		channel = connectionFuture.channel();

		return connectionFuture;
	}

	private boolean enableSSL;

	public void enableSSL(boolean set) {
		enableSSL = set;
	}

	public boolean sslEnabled() {
		return enableSSL;
	}

	/**
	 * Disconnects the connection
	 * 
	 * @return A {@link Future} that is notified when the disconnection operation is
	 *         complete.
	 */
	public Future<?> disconnect() {
		if (!isConnectionActive()) {
			return null;
		}
		disconnectCalled = true;
		channel.disconnect();
		Future<?> shutdownFuture = group.shutdownGracefully();
		return shutdownFuture;
	}

	/**
	 * Returns a {@link ChannelFuture} that will be notified when this instance's
	 * channel is closed, null if this instance has no channel.
	 */
	public ChannelFuture closeFuture() {
		if (channel == null) {
			return null;
		}
		return channel.closeFuture();
	}

	/**
	 * Changing the host name will not update the connection unless this instance is
	 * closed and reopened
	 */
	public void setHost(String host) {
		this.host = host;
	}

	public String getHost() {
		return host;
	}

	/**
	 * Changing the port number will not update the connection unless this instance
	 * is closed and reopened
	 */
	public void setPort(int port) {
		this.port = port;
	}

	public int getPort() {
		return port;
	}

	/**
	 * @return Whether this instance's channel is both connected and open
	 */
	public boolean isConnectionActive() {
		if (channel == null) {
			return false;
		}
		return channel.isActive();
	}

	/**
	 * @return Whether this instance is trying to connect, ignoring if this instance
	 *         is actually connected.
	 */
	public boolean isConnecting() {
		return connectCalled;
	}

	/**
	 * @return If a connection attempt has been made on this instance and completed,
	 *         ignoring whether or not the connection was successful.
	 */
	public boolean connectionAttempted() {
		if (connectionFuture == null) {
			return false;
		}
		return connectionFuture.isDone();
	}

	/**
	 * @return If the close of the connection was unexpected, returns false if
	 *         connection is still active
	 */
	public boolean isUnexpectedClose() {
		return !isConnectionActive() && !disconnectCalled;
	}

	public boolean isWritable() {
		if (channel == null) {
			return false;
		}
		return channel.isWritable();
	}

	protected Channel getChannel() {
		return channel;
	}
}
