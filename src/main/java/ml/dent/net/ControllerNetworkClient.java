package ml.dent.net;

import java.util.concurrent.ConcurrentLinkedQueue;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

public class ControllerNetworkClient extends SimpleNetworkClient {

	private ConcurrentLinkedQueue<String>	textQ;
	private StringBuffer					addText;

	public ControllerNetworkClient(String host, int port) {
		super(host, port, '0');
		textQ = new ConcurrentLinkedQueue<>();
		addText = new StringBuffer();
	}

	public boolean isTextReady() {
		return !textQ.isEmpty();
	}

	public String getNextText() {
		return textQ.poll();
	}

	@Override
	public ChannelFuture connect() {
		return super.connect(new ControllerOutboundHandler(), new ControllerInboundHandler());
	}

	@Override
	public ChannelFuture connect(ChannelHandler... channelHandlers) {
		ChannelHandler[] newHandlers = new ChannelHandler[channelHandlers.length + 2];
		newHandlers[0] = new ControllerOutboundHandler();
		newHandlers[1] = new ControllerInboundHandler();
		for (int i = 2; i < newHandlers.length; i++) {
			newHandlers[i] = channelHandlers[i - 2];
		}

		return super.connect(newHandlers);
	}

	public void stopMill() {
		writeAndFlush(Markers.STOP);
	}

	public void jogMill(int direction) {
		int dir = (int) Math.signum(direction);
		write(Markers.JOG);
		write((byte) dir);
		flush();
	}

	public void setSpeed(int speed) {
		write(Markers.SPEED);
		write((byte) speed);
		flush();
	}

	public void setAxis(String axis) {
		int axisNum;
		switch (axis) {
			case "X":
				axisNum = 0;
				break;
			case "Y":
				axisNum = 1;
				break;
			case "Z":
				axisNum = 2;
				break;
			case "A":
				axisNum = 3;
				break;
			default:
				axisNum = 0;
		}

		write(Markers.AXIS);
		write((byte) axisNum);
		flush();
	}

	private static class Markers {
		public static final byte	PING_REQUEST	= 0x7f;
		public static final byte	PING_RESPONSE	= 0x7e;
		public static final byte	STOP			= 0x65;
		public static final byte	SPEED			= 0x73;
		public static final byte	JOG				= 0x6a;
		public static final byte	AXIS			= 0x61;
		public static final byte	MSG				= 0x6d;
		public static final byte	ESC_MSG			= 0x00;
	}

	private String closeReason;

	public String getCloseReason() {
		return closeReason;
	}

	private class ControllerInboundHandler extends ChannelInboundHandlerAdapter {

		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			super.channelActive(ctx);
		}

		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
			ByteBuf buffer = (ByteBuf) msg;
			byte[] bytes = new byte[buffer.readableBytes()];
			buffer.readBytes(bytes);

			boolean inMsg = false;

			for (int i = 0; i < bytes.length; i++) {
				if (inMsg) {
					if (bytes[i] == Markers.ESC_MSG) {
						inMsg = false;
						break;
					}
					if (bytes[i] == '\n') {
						addText.append((char) bytes[0]);
						textQ.offer(addText.toString());
						addText = new StringBuffer();
					} else {
						addText.append((char) bytes[0]);
					}
				} else {
					switch (bytes[i]) {
						case Markers.PING_REQUEST:
							writeAndFlush(Markers.PING_RESPONSE);
							break;
						case Markers.MSG:
							inMsg = true;
					}
				}
			}

			super.channelRead(ctx, msg);
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
			cause.printStackTrace();
			ctx.close();
		}
	}

	private class ControllerOutboundHandler extends ChannelOutboundHandlerAdapter {
		@Override
		public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
			super.write(ctx, msg, promise);
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
			cause.printStackTrace();
			ctx.close();
			closeReason = cause.getLocalizedMessage();
		}
	}
}