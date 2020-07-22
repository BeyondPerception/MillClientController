package ml.dent.net;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import ml.dent.util.Markers;

import java.util.concurrent.ConcurrentLinkedQueue;

public class ControllerNetworkClient extends SimpleNetworkClient {

    private ConcurrentLinkedQueue<String> textQ;
    private StringBuffer                  addText;

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

    public void requestVideo() {
        writeAndFlush(Markers.START_VIDEO);
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
    }
}