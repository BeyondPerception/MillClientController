package ml.dent.net;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import ml.dent.app.StatusHandler;
import ml.dent.util.DaemonThreadFactory;
import ml.dent.util.Markers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ControllerNetworkClient extends SimpleNetworkClient {

    private ScheduledThreadPoolExecutor pingScheduler = new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory());
    private ScheduledFuture<?>          pingFuture;

    private StatusHandler logger = StatusHandler.getInstance();

    public ControllerNetworkClient(String host, int port) {
        super(host, port, '0');
        pingScheduler.setRemoveOnCancelPolicy(true);
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

    private BooleanProperty isMillAccessible = new SimpleBooleanProperty(false);

    public ReadOnlyBooleanProperty millAccessProperty() {
        return isMillAccessible;
    }

    private BooleanProperty pingSent = new SimpleBooleanProperty(false);

    public boolean awaitNextPing(long timeout) throws InterruptedException {
        CountDownLatch wait = new CountDownLatch(1);
        InvalidationListener pingChange = (listener) -> {
            if (!pingSent.get()) {
                wait.countDown();
            }
        };
        pingSent.addListener(pingChange);
        boolean recvPing = wait.await(timeout, TimeUnit.MILLISECONDS);
        pingSent.removeListener(pingChange);
        return recvPing;
    }

    private class ControllerInboundHandler extends ChannelInboundHandlerAdapter {

        private long count;

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            count = 0;
            pingFuture = pingScheduler.scheduleAtFixedRate(() -> {
                if (pingSent.get()) {
                    isMillAccessible.set(false);
                }
                if (count % 2 == 0 || !pingSent.get() && isConnectionActive()) {
                    count = 0;
                    writeAndFlush(Markers.PING_REQUEST);
                    pingSent.set(true);
                }
                count++;
            }, 0L, 3L, TimeUnit.SECONDS);

            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            pingFuture.cancel(true);
            pingSent.set(false);
            isMillAccessible.set(false);
            super.channelInactive(ctx);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ByteBuf buffer = (ByteBuf) msg;
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);

            for (byte b : bytes) {
                if (b == Markers.PING_REQUEST) {
                    logger.offerStatus("Recv ping request", StatusHandler.MORE);
                    writeAndFlush(Markers.PING_RESPONSE);
                } else if (b == Markers.PING_RESPONSE) {
                    logger.offerStatus("Recv ping response", StatusHandler.MORE);
                    if (pingSent.get()) {
                        pingSent.set(false);
                    }
                    isMillAccessible.set(true);
                } else {
                    logger.offerStatus("Recv unknown byte: " + b, StatusHandler.MORE);
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