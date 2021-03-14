import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.net.*;
    
    
    
	class ClientToServerHttpsTransmitter implements Runnable {

		InputStream clientStream;
		OutputStream serverStream;

		public ClientToServerHttpsTransmitter(InputStream proxyToClientIS, OutputStream proxyToServerOS) {
			this.clientStream = proxyToClientIS;
			this.serverStream = proxyToServerOS;
		}

		@Override
		public void run() {
			try {
				byte[] buffer = new byte[4096];
				int read;
				do {
					read = clientStream.read(buffer);
					if (read > 0) {
						serverStream.write(buffer, 0, read);
						if (clientStream.available() < 1) {
							serverStream.flush();
						}
					}
				} while (read >= 0);
			} catch (SocketTimeoutException e) {
                System.out.println("SocketTimeoutExceptio");
			} catch (IOException e) {
				System.out.println("IOException");
			}
		}
	}