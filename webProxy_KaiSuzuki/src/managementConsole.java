import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.net.*;

@SuppressWarnings("unchecked")
public class managementConsole implements Runnable {

	
	private Socket browserSocket;
	private BufferedReader clientR;
	private BufferedWriter clientW;
	private Thread client2ServerHttpsThread;

	
	private static final String CONNECT = "CONNECT";


	//constructor
	public managementConsole(Socket browserSocket) {
		this.browserSocket = browserSocket;
		try {
			this.browserSocket.setSoTimeout(2000);
			clientR = new BufferedReader(new InputStreamReader(browserSocket.getInputStream()));
			clientW = new BufferedWriter(new OutputStreamWriter(browserSocket.getOutputStream()));
		} catch (IOException e) {
			System.out.println("IOException");
		}
	}

	
	@Override
	public void run() {
		String requestString, requestType, requestUrl;
		try {
			requestString = clientR.readLine();
			String[] splittedRequest = getRequestType_url(requestString);
			requestType = splittedRequest[0];
			requestUrl = splittedRequest[1];
		} catch (IOException e) {
			System.out.println("IOException");
			return;
		}

		if (proxyServer.isBlocked(requestUrl+":403")) {
			System.out.println(requestUrl + " is blocked.");
			blockedRequested();
            System.out.println(requestUrl + " is blocked and can't be accessed");
			return;
		}

		switch (requestType) {
			case CONNECT:
				System.out.println("HTTPS request: " + requestUrl);
				HTTPSRequest(requestUrl);
				break;
			default:
				File file = proxyServer.getCachedPage(requestUrl);
				if (file == null) {
					System.out.println("HTTP request: " + requestUrl + ". It is not cached");
					nonCachedRequest(requestUrl);
				} else {
					System.out.println(requestUrl + "can be connected from cached page");
					cachedRequest(file);
				}
				break;
		}
	}

	// recogniese the request is GET or CONNECT and find requestURL
	private String[] getRequestType_url(String request) {
		String requestType, requestUrl;
		int requestSeparator;
		requestSeparator = request.indexOf(' ');
		requestType = request.substring(0, requestSeparator);
		requestUrl = request.substring(requestSeparator + 1);
		requestUrl = requestUrl.substring(0, requestUrl.indexOf(' '));
		if (!requestUrl.substring(0, 4).equals("http")) {
			requestUrl = "http://" + requestUrl;
		}
		return new String[] { requestType, requestUrl };
	}

	// cache site request
	private void cachedRequest(File file) {
		try {

            // If file is an image write data to client using buffered image.
			String fileExtension = file.getName().substring(file.getName().lastIndexOf('.'));
            if((fileExtension.contains(".png")) || fileExtension.contains(".jpg") || fileExtension.contains(".jpeg") || fileExtension.contains(".gif")){	

                BufferedImage image = ImageIO.read(file);

				if (image == null) {
					System.out.println("Image " + file.getName() + " was null");
					String response = getResponse(404, false);
					clientW.write(response);
					clientW.flush();
				} 
                
                else {
					String response = getResponse(200, false);
					clientW.write(response);
					clientW.flush();
					ImageIO.write(image, fileExtension.substring(1), browserSocket.getOutputStream());
				}
			} 
            
            // normal text based file requested
            else {
				BufferedReader cachedFileBufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
				String response = getResponse(200, false);
				clientW.write(response);
				clientW.flush();
				String line;
				while ((line = cachedFileBufferedReader.readLine()) != null) {
					clientW.write(line);
				}
				clientW.flush();

				if (cachedFileBufferedReader != null) {
					cachedFileBufferedReader.close();
				}
			}
			if (clientW != null) {
				clientW.close();
			}
		} catch (IOException e) {
			System.out.println("Error sending cached file to client");
		}
	}

	//non cached site request
	private void nonCachedRequest(String url) {
		try {

            //deal file extention
			int fileExtensionIndex = url.lastIndexOf(".");
			String fileExtension = url.substring(fileExtensionIndex, url.length());
			String fileName = url.substring(0, fileExtensionIndex);
			fileName = fileName.substring(fileName.indexOf('.') + 1);
			fileName = fileName.replace("/", "__");
			fileName = fileName.replace('.', '_');

			if (fileExtension.contains("/")) {
				fileExtension = fileExtension.replace("/", "__");
				fileExtension = fileExtension.replace('.', '_');
				fileExtension += ".html";
			}

			fileName = fileName + fileExtension;


			boolean caching = true;
			File fileToCache = null;
			BufferedWriter cacheWriter = null;

			try {
				fileToCache = new File("cache/" + fileName);
				if (!fileToCache.exists()) {
					fileToCache.createNewFile();
				}
				cacheWriter = new BufferedWriter(new FileWriter(fileToCache));
			} catch (IOException e) {
				System.out.println("Error trying to cache " + fileName);
				caching = false;
			} catch (NullPointerException e) {
				System.out.println("Null pointer opening file " + fileName);
			}


            // Check if file is an image
			if((fileExtension.contains(".png")) || fileExtension.contains(".jpg") || fileExtension.contains(".jpeg") || fileExtension.contains(".gif")){
				URL remoteURL = new URL(url);
				BufferedImage image = ImageIO.read(remoteURL);

				if (image != null) {
					ImageIO.write(image, fileExtension.substring(1), fileToCache);
					String line = getResponse(200, false);
					clientW.write(line);
					clientW.flush();
					ImageIO.write(image, fileExtension.substring(1), browserSocket.getOutputStream());

				} 
                
                else {
					String error = getResponse(404, false);
					clientW.write(error);
					clientW.flush();
					return;
				}
			} 
            

            // File is a text file
            else {
				URL remoteURL = new URL(url);
				HttpURLConnection serverConnection = (HttpURLConnection) remoteURL.openConnection();
				serverConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
				serverConnection.setRequestProperty("Content-Language", "en-US");
				serverConnection.setUseCaches(false);
				serverConnection.setDoOutput(true);
				BufferedReader proxyToServerBR = new BufferedReader(
						new InputStreamReader(serverConnection.getInputStream()));
				String line = getResponse(200, false);
				clientW.write(line);
				while ((line = proxyToServerBR.readLine()) != null) {
					clientW.write(line);
					if (caching) {
						cacheWriter.write(line);
					}
				}
				clientW.flush();
				if (proxyToServerBR != null) {
					proxyToServerBR.close();
				}
			}

			if (caching) {
				cacheWriter.flush();
				proxyServer.addCachedPage(url, fileToCache);
			}
			if (cacheWriter != null) {
				cacheWriter.close();
			}
			if (clientW != null) {
				clientW.close();
			}
		} catch (Exception e) {
			System.out.println("Exception");
		}
	}

	// HTTPS request
	private void HTTPSRequest(String url) {

		String urlTetx = url.substring(7);
		String pieces[] = urlTetx.split(":");
		urlTetx = pieces[0];
		int serverPort = Integer.valueOf(pieces[1]);

		try {
			// Only the first line of the CONNECT request has been processed. 
			for (int i = 0; i < 5; i++) {
				clientR.readLine();
			}


			// Open new connected socket
			InetAddress serverAddress = InetAddress.getByName(urlTetx);
			Socket serverSocket = new Socket(serverAddress, serverPort);
			serverSocket.setSoTimeout(5000);

			String line = getResponse(200, true);
			clientW.write(line);
			clientW.flush();

			
			BufferedWriter serverWriter = new BufferedWriter(new OutputStreamWriter(serverSocket.getOutputStream()));
			BufferedReader serverReader = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));


            //Create new thread
			ClientToServerHttpsTransmitter client2ServerHttps = new ClientToServerHttpsTransmitter(browserSocket.getInputStream(), serverSocket.getOutputStream());
            client2ServerHttpsThread = new Thread(client2ServerHttps);
			client2ServerHttpsThread.start();

			// Handle communication between the server and the client
			try {
				byte[] buffer = new byte[4096];
				int read;
				do {
					read = serverSocket.getInputStream().read(buffer);
					if (read > 0) {
						browserSocket.getOutputStream().write(buffer, 0, read);
						if (serverSocket.getInputStream().available() < 1) {
							browserSocket.getOutputStream().flush();
						}
					}
				} while (read >= 0);


			} catch (SocketTimeoutException e) {
				System.out.println("Socket timeout during HTTPs connection");
			} catch (IOException e) {
				System.out.println("Error handling HTTPs connection");
			}




			// Close  resources
			if (serverSocket != null) {
                serverSocket.close();
            }
            if (serverReader != null) {
                serverReader.close();
            }
            if (serverWriter != null) {
                serverWriter.close();
            }
            if (clientW != null) {
                clientW.close();
            }





		} catch (SocketTimeoutException e) {
			String line = getResponse(504, false);
			try {
				clientW.write(line);
				clientW.flush();
			} catch (IOException x) {
			}
		} catch (Exception e) {
			System.out.println("Error on HTTPS " + url);
		}
	}

	

    private void blockedRequested(){
        
		try {
         
			BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(browserSocket.getOutputStream()));
			String line = "HTTP/1.0 403 Access Forbidden \n" +
					"User-Agent: ProxyServer/1.0\n" +
					"\r\n";
			bufferedWriter.write(line);
			bufferedWriter.flush();
            
		} catch (IOException e) {
			System.out.println("Error writing to client when requested a blocked site");
			e.printStackTrace();
		}
	}

	

	// Return certain response strings based on the error code passed. There are two
	// cases for 200, 'OK' and 'Connection established', in which case check the
	// boolean.
	private String getResponse(int code, boolean connectionEstablished) {
		String response = "";
		switch (code) {
			case 200:
				if (connectionEstablished) {
					response = "HTTP/1.0 200 Connection established\r\nProxy-Agent: ProxyServer/1.0\r\n\r\n";
				} else {
					response = "HTTP/1.0 200 OK\nProxy-agent: ProxyServer/1.0\n\r\n";
				}
				break;
			case 403:
				response = "HTTP/1.0 403 Access Forbidden \nUser-Agent: ProxyServer/1.0\n\r\n";
				break;
			case 404:
				response = "HTTP/1.0 404 NOT FOUND \nProxy-agent: ProxyServer/1.0\n\r\n";
				break;
			case 504:
				response = "HTTP/1.0 504 Timeout Occured after 10s\nUser-Agent: ProxyServer/1.0\n\r\n";
				break;
			default:
				break;
		}
		return response;
	}

	

	// // Seperate class to handle HTTPS transmission from the client to the server. In
	// // practise it is spun off as a seperate thread to run alongside transmission
	// // from the server to the client.
	// class ClientToServerHttpsTransmitter implements Runnable {

	// 	InputStream clientStream;
	// 	OutputStream serverStream;

	// 	public ClientToServerHttpsTransmitter(InputStream proxyToClientIS, OutputStream proxyToServerOS) {
	// 		this.clientStream = proxyToClientIS;
	// 		this.serverStream = proxyToServerOS;
	// 	}

	// 	@Override
	// 	public void run() {
	// 		try {
	// 			byte[] buffer = new byte[4096];
	// 			int read;
	// 			do {
	// 				read = clientStream.read(buffer);
	// 				if (read > 0) {
	// 					serverStream.write(buffer, 0, read);
	// 					if (clientStream.available() < 1) {
	// 						serverStream.flush();
	// 					}
	// 				}
	// 			} while (read >= 0);
	// 		} catch (SocketTimeoutException e) {
	// 		} catch (IOException e) {
	// 			System.out.println("Proxy to client HTTPS read timed out");
	// 		}
	// 	}
	// }
}