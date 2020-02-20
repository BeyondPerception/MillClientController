import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

public class test2 {
	public static void main(String[] args) throws IOException {
//		ClientNetworkClient client = new ClientNetworkClient("localhost", 1111);
//		ChannelFuture cf = client.connect();
//		cf.awaitUninterruptibly();

		BufferedImage bf = ImageIO.read(new File("/home/ronak/Downloads/red.jpg"));
		byte[] bytes = new byte[1 << 16];
		InputStream os = new ByteArrayInputStream(bytes);
		
		
		
	}
}
