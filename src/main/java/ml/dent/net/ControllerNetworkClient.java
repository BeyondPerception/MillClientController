package ml.dent.net;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import ml.dent.util.Markers;

public class ControllerNetworkClient extends SimpleNetworkClient {

    public ControllerNetworkClient(String host, int port) {
        super(host, port, '0');
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
        System.arraycopy(channelHandlers, 0, newHandlers, 2, newHandlers.length - 2);

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
        int axisNum = 0;
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
        }

        write(Markers.AXIS);
        write((byte) axisNum);
        flush();
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

            for (byte aByte : bytes) {
                if (aByte == Markers.PING_REQUEST) {
                    writeAndFlush(Markers.PING_RESPONSE);
                }
            }

            super.channelRead(ctx, msg);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }

    private static class ControllerOutboundHandler extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            super.write(ctx, msg, promise);
        }
    }
}