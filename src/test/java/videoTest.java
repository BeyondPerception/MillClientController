//import javax.swing.JFrame;
//
//import ml.dent.video.VideoClient;
//import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery;
//import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;
//
//public class videoTest {
//	public static void main(String[] args) {
//		VideoClient videoClient = new VideoClient("localhost", 1111);
//		videoClient.connect();
//		while (!videoClient.isConnectionActive())
//			;
//
//		new NativeDiscovery().discover();
//		JFrame frame = new JFrame("A GUI");
//		frame.setBounds(100, 100, 600, 400);
//		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
//
//		EmbeddedMediaPlayerComponent mediaPlayerComponent = new EmbeddedMediaPlayerComponent();
//
//		mediaPlayerComponent = new EmbeddedMediaPlayerComponent();
//		frame.setContentPane(mediaPlayerComponent);
//
//		frame.setVisible(true);
//
//		mediaPlayerComponent.mediaPlayer().media().play(videoClient.startVideo());
//	}
//}
//
////class Test extends ChannelInboundHandlerAdapter {
////	@Override
////	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
////		ByteBuf buf = (ByteBuf) msg;
////		byte[] bytes = new byte[buf.readableBytes()];
////		buf.readBytes(bytes);
////
////		int c = 0;
////		for (byte b : bytes) {
////			System.out.print(b + " ");
////			c++;
////			if (c == 10) {
////				System.out.println();
////				c = 0;
////			}
////		}
////
////		super.channelRead(ctx, msg);
////	}
////}
