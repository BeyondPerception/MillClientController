
import java.util.ArrayList;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.util.CharsetUtil;

public class test {

	static EventLoopGroup	workerGroup;
	static String			host;
	static int				port;

	public static void main(String[] args) throws Exception {

//		ClientNetworkClient client = new ClientNetworkClient("localhost", 1111);
//		ChannelFuture cf = client.connect(new ClientHandler());
//
//		while (!cf.isDone()) {
//			System.out.println("Connecting...");
//		}
//		System.out.println(client.isConnectionActive());

//		String test = "Hx9YY7ucKfs7OVqdRSI2xPWO3FNDEtYGw6QgsqzKvpUGBggEnYM3EdoOel3N582arTAvIsGO1XwmzHCVeYMpATQeaV3WVroxpeAkbvRUoIpreFoJnMFqsbvyln1QQcQuhQbEI8Frx8BIbaLaiRFQgqrznhR3o9MR83fRtQNQ9P6B7uhNgDSg8W4SS7eflY49rC6kI1kVTy1P40twlhffqQpI4Ny6dNBWyzBtZx7XcdFg2m2zJk2AeP31koB1L7vIeLegXcbs5Wf7htq08Z5pbKbhRXdysTcOymaWIbalpdxe9HxI82r7gU04nxlycq21fxYod8wkycCmINSI2d1uqup502AR6kGYdbizXNfVFlDGVtTBESomisilu7dfYQbqPUKcVOc2Ne132yEo3gPhBb0jbQ6H9ieFE8M3xFzytXVNgmTHq4mLVnfg4qxRi1Zyg7CY92Cf7z2bcGdzA41PHkCYw6rtpPtzpkZby30jR6DFNIoMOL2mvk3OpbKCdz52VBe1DUyQPniGMbaJdALf6AeNFsIVBdDKdylgyxHBXqRQUvTKkYIAyb0DjVGL6fIC2fvPUOa0eDVuH9e4Ey3ixHBHymm4c1s5ABr44In6IGjLrxvSXzxwtuERJNiazJdZ9MBU3oQmCgNcTPLNrQwC7vSVv433OZNopvvBgDYjjLWCFlnsH7svmTUWhqeK0HHxrMyAZnqWrBLrUi4MgNSH9RKFZojBOtHE6L7i5rzuKrAWZ8SmaGnmKDeLYwbFQ0n6VdVmgGX3Y409KjznRKbNBMQk77C3Kq7IDgEHvLXbkbO6nLbyI3l2Ve4hhQQSGScCuFHWldV7l9NeNDuhOvkJInLCn4mkLpz4CbxHBWnvba0wv2ALklRJxjLGgTZoMigq4Xmtp4PvIUpiVAgrjMbuqklwHk3t5DIvhL8LIQLmYDtelslEwwmltlri52BbMWkbAyCWGVeUHM91sjPgg2SIiafJKRHAOBhAngImXTuDuQ84kF57g2OUhC3WpK2Hszzu";

	}

//	public static void gen() {
//		long i = 0;
//		while (res.toString().replace(" ", "").length() <= 10006) {
//			if (new BigInteger(i + "").isProbablePrime(1)) {
//				res.append(i).append(" ");
//			}
//			i++;
//		}
//	}

	static class ClientHandler extends ChannelInboundHandlerAdapter {

		@Override
		public void channelRead(ChannelHandlerContext arg0, Object msg) throws Exception {
			ByteBuf buffer = (ByteBuf) msg;
			try {
				System.out.println(buffer.toString(CharsetUtil.UTF_8));
			} finally {
				buffer.release();
			}
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext channelHandlerContext, Throwable cause) {
			cause.printStackTrace();
			channelHandlerContext.close();
		}

	}
}
