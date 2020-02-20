package ml.dent.video;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.freedesktop.gstreamer.Buffer;
import org.freedesktop.gstreamer.Bus;
import org.freedesktop.gstreamer.Caps;
import org.freedesktop.gstreamer.FlowReturn;
import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.Sample;
import org.freedesktop.gstreamer.Structure;
import org.freedesktop.gstreamer.elements.AppSink;
import org.freedesktop.gstreamer.elements.AppSrc;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import ml.dent.net.SimpleNetworkClient;

public class VideoClient extends SimpleNetworkClient {

	private ConcurrentLinkedQueue<Byte>	incomingBytes;
	private InputStream					byteStream;

	public VideoClient(String host, int port) {
		super(host, port, '1');
		incomingBytes = new ConcurrentLinkedQueue<Byte>();
		byteStream = new InputStream() {
			@Override
			public int read() throws IOException {
				while (incomingBytes.isEmpty())
					;
				return incomingBytes.poll();
			}
		};
	}

	@Override
	public ChannelFuture connect() {
		return super.connect(new VideoReceiver());
	}

	@Override
	public ChannelFuture connect(ChannelHandler... channelHandlers) {
		ChannelHandler[] newHandlers = new ChannelHandler[channelHandlers.length + 1];
		newHandlers[0] = new VideoReceiver();
		for (int i = 1; i < newHandlers.length; i++) {
			newHandlers[i] = channelHandlers[i - 1];
		}

		return super.connect(newHandlers);
	}

//	public NonSeekableInputStreamMedia startVideo() {
//		if (!isConnectionReady()) {
//			throw new IllegalStateException("Cannot start stream, connection not ready!");
//		}
//
//		NonSeekableInputStreamMedia callback = new NonSeekableInputStreamMedia() {
//			@Override
//			protected long onGetSize() {
//				return 0;
//			}
//
//			@Override
//			protected InputStream onOpenStream() throws IOException {
//				return byteStream;
//			}
//
//			@Override
//			protected void onCloseStream(InputStream inputStream) throws IOException {
//				byteStream.close();
//			}
//		};
//
//		return callback;
//	}
//
//	public void stopVideo() {
//
//	}

	private Pipeline pipeline;

	public void startVideo(ImageView iv) {
		Gst.init();

		String parseString = "appsrc name=src is-live=true ! queue ! decodebin ! videoconvert ! appsink name=sink sync=false";

		pipeline = (Pipeline) Gst.parseLaunch(parseString);
		AppSrc src = (AppSrc) pipeline.getElementByName("src");
		src.set("emit-signals", true);
		src.connect(new AppSrc.NEED_DATA() {
			@Override
			public void needData(AppSrc elem, int size) {
				Buffer buf = new Buffer(size);
				byte[] bytes = new byte[size];

				for (int i = 0; i < size; i++) {
					try {
						bytes[i] = (byte) byteStream.read();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				buf.map(true).put(ByteBuffer.wrap(bytes));
				elem.pushBuffer(buf);
			}
		});

		AppSink sink = (AppSink) pipeline.getElementByName("sink");
		sink.set("emit-signals", true);
		sink.connect(new AppSink.NEW_SAMPLE() {

			private Image	actualFrame;
			private int		lastWidth	= 0;
			private int		lastHeigth	= 0;
			private byte[]	byteArray;

			@Override
			public FlowReturn newSample(AppSink elem) {
				Sample sample = elem.pullSample();
				Buffer buffer = sample.getBuffer();
				ByteBuffer byteBuffer = buffer.map(false);

				if (byteBuffer != null) {
					Structure capsStruct = sample.getCaps().getStructure(0);
					int width = capsStruct.getInteger("width");
					int height = capsStruct.getInteger("height");
					if (width != lastWidth || height != lastHeigth) {
						lastWidth = width;
						lastHeigth = height;
						byteArray = new byte[width * height * 4];
					}
					byteBuffer.get(byteArray);
					actualFrame = convertBytesToImage(byteArray, width, height);

					iv.setImage(actualFrame);
					buffer.unmap();
				}
				sample.dispose();
				return FlowReturn.OK;
			}
		});

		StringBuilder caps = new StringBuilder("video/x-raw, ");
		// JNA creates ByteBuffer using native byte order, set masks according to that.
		if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
			caps.append("format=BGRx");
		} else {
			caps.append("format=xRGB");
		}
		sink.setCaps(new Caps(caps.toString()));
		sink.set("max-buffers", 5000);
		sink.set("drop", true);

//		pipeline.getBus().connect((Bus.EOS) (source) -> {
//			System.out.println("Received the EOS on the pipeline!!!");
//		});
//
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

		pipeline.play();
	}

	private Image convertBytesToImage(byte[] pixels, int width, int height) {
		// Writes a bytearray to a WritableImage.
		WritableImage img = new WritableImage(width, height);
		PixelWriter pw = img.getPixelWriter();
		pw.setPixels(0, 0, width, height, PixelFormat.getByteBgraInstance(), pixels, 0, width * 4);
		return img;
	}

	public void stopVideo() {
		if (pipeline == null) {
			System.out.println("Pipeline is null, returning");
			return;
		}
		System.out.println("Stopping Pipeline");
		pipeline.stop();
		System.out.println("Closing pipline");
		pipeline.close();
		System.out.println("Deinit of Gstreamer");
		Gst.deinit();
		while (Gst.isInitialized())
			System.out.println("Closing");
		;
	}

	private class VideoReceiver extends ChannelInboundHandlerAdapter {

		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
			ByteBuf buf = (ByteBuf) msg;
			byte[] bytes = new byte[buf.readableBytes()];
			buf.readBytes(bytes);

			for (byte b : bytes) {
				incomingBytes.offer(b);
			}

			super.channelRead(ctx, msg);
		}
	}
}
