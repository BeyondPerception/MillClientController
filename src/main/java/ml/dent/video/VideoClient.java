package ml.dent.video;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import ml.dent.app.StatusHandler;
import ml.dent.net.SimpleNetworkClient;
import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.elements.AppSink;
import org.freedesktop.gstreamer.elements.AppSrc;

import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

public class VideoClient extends SimpleNetworkClient {

    private LinkedBlockingQueue<Byte> incomingBytes;

    private StatusHandler logger = StatusHandler.getInstance();

    public VideoClient(String host, int port) {
        super(host, port, '1');
        incomingBytes = new LinkedBlockingQueue<>();
    }

    @Override
    public ChannelFuture connect() {
        return super.connect(new VideoReceiver());
    }

    @Override
    public ChannelFuture connect(ChannelHandler... channelHandlers) {
        ChannelHandler[] newHandlers = new ChannelHandler[channelHandlers.length + 1];
        newHandlers[0] = new VideoReceiver();
        System.arraycopy(channelHandlers, 0, newHandlers, 1, newHandlers.length - 1);

        return super.connect(newHandlers);
    }

    private Pipeline pipeline;

    private BooleanProperty playingProperty = new SimpleBooleanProperty(false);

    public ReadOnlyBooleanProperty playingProperty() {
        return BooleanProperty.readOnlyBooleanProperty(playingProperty);
    }

    public boolean isPlaying() {
        return pipeline.isPlaying();
    }

    public void startVideo(ImageView iv) {
        System.out.println("Initializing Gstreamer...");
        Gst.init();

        while (!Gst.isInitialized())
            ;
        System.out.println("Gstreamer initialized");

        String parseString = "appsrc name=src is-live=true ! queue ! decodebin ! videoconvert ! appsink name=sink sync=false";

        System.out.println("Building pipeline");
        pipeline = (Pipeline) Gst.parseLaunch(parseString);
        AppSrc src = (AppSrc) pipeline.getElementByName("src");
        src.set("emit-signals", true);
        src.connect((AppSrc.NEED_DATA) (elem, size) -> {
            Buffer buf = new Buffer(size);
            byte[] bytes = new byte[size];

            for (int i = 0; i < size; i++) {
                try {
                    bytes[i] = incomingBytes.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            buf.map(true).put(ByteBuffer.wrap(bytes));
            elem.pushBuffer(buf);
        });

        AppSink sink = (AppSink) pipeline.getElementByName("sink");
        sink.set("max-buffers", 5000);
        sink.set("drop", true);
        FXImageSink imageSink = new FXImageSink(sink);
        ReadOnlyObjectProperty<Image> prop = imageSink.imageProperty();
        iv.imageProperty().bind(prop);

        pipeline.getBus().connect((Bus.ERROR) (source, code, message) -> {
            System.out.println("Error Source: " + source.getName());
            System.out.println("Error Code: " + code);
            System.out.println("Error Message: " + message);
        });
        pipeline.getBus().connect((Bus.INFO) (source, code, message) -> {
            System.out.println("Info Source: " + source.getName());
            System.out.println("Info Code: " + code);
            System.out.println("Info Message: " + message);
        });

//		pipeline.getBus().connect((Bus.MESSAGE) (bus, message) -> {
//			System.out.println("Bus Message : " + message.getStructure());
//		});

        System.out.println("Playing video");
        pipeline.play();
    }

    public void stopVideo() {
        if (pipeline == null) {
            System.out.println("Pipeline is null, returning");
            return;
        }
        if (pipeline.isPlaying()) {
            pipeline.stop();
        }
        System.out.println("Closing pipline");
        pipeline.close();
    }

    private class VideoReceiver extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ByteBuf buf = (ByteBuf) msg;
            while (buf.readableBytes() > 0) {
                incomingBytes.offer(buf.readByte());
            }

            super.channelRead(ctx, msg);
        }
    }
}
