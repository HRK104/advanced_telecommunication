

import java.io.*;
import java.net.*;
import java.util.*;

@SuppressWarnings("unchecked")
public class proxyServer implements Runnable {

	private static final String BLOCKED = "BLOCKED";
	private static final String CACHED = "CACHED";
	private static final String CLOSE = "CLOSE";

	private static HashMap<String, File> cachedMap;
	private static HashMap<String, String> blockedMap;
	private static ArrayList<Thread> threads;

	
	// find the cached page if it is found in cached
	public static File getCachedPage(String url) {
		return cachedMap.get(url);
	}

	// Add a cache site
	public static void addCachedPage(String urlString, File fileToCache) {
		cachedMap.put(urlString, fileToCache);
	}

	// Go through the blocked list to check whether the site is blocked
	public static boolean isBlocked(String url) {
		for (String key : blockedMap.keySet()) {
			if (url.contains(key)) {
				return true;
			}
		}
		return false;
	}

	
    
	public static void main(String[] args) {
		proxyServer proxy = new proxyServer();
		proxy.listen();
	}

	
	private int browserPort;
	private ServerSocket browserListener;
	private volatile boolean running = true;
	private Thread managementConsole;

	//constructor 
	public proxyServer() {
		// initialise all of the data
		cachedMap = new HashMap<>();
		blockedMap = new HashMap<>();
		threads = new ArrayList<>();
		browserPort = 8000;

		managementConsole = new Thread(this);
		managementConsole.start();

		// Initalize cached and blocked site
		initializeCached();
		initializeBlocked();

		try {
			browserListener = new ServerSocket(browserPort);
			System.out.println("Waiting for client on port: " + browserListener.getLocalPort());
			running = true;
		} catch (SocketException e) {
			System.out.println("SocketException");
		} catch (SocketTimeoutException e) {
			System.out.println("SocketTimeoutException");
		} catch (IOException e) {
			System.out.println("IOException");
		}
	}



	public void listen() {
		while (running) {
			try {
				Socket socket = browserListener.accept();
				Thread thread = new Thread(new managementConsole(socket));
				threads.add(thread);
				thread.start();
			} catch (SocketException e) {
				System.out.println("Server closed");
			} catch (IOException e) {
				System.out.println("Error creating new Thread from ServerSocket.");
			}
		}
	}


	//initialise the cached sites
	private void initializeCached() {
		try {
			File cachedSites = new File("cached.txt");
			if (!cachedSites.exists()) {
				cachedSites.createNewFile();
			} else {
				FileInputStream cachedFileStream = new FileInputStream(cachedSites);
				ObjectInputStream cachedObjectStream = new ObjectInputStream(cachedFileStream);
				cachedMap = (HashMap<String, File>) cachedObjectStream.readObject();
				cachedFileStream.close();
				cachedObjectStream.close();
			}
		} catch (IOException e) {
			System.out.println("IOException");
		} catch (ClassNotFoundException e) {
			System.out.println("ClassNotFoundException");
		}
	}

	//initialise blocked sites
	private void initializeBlocked() {
		try {
			File blockedSitesTxtFile = new File("blocked.txt");
			if (!blockedSitesTxtFile.exists()) {
				blockedSitesTxtFile.createNewFile();
			} else {
				FileInputStream blockedFileStream = new FileInputStream(blockedSitesTxtFile);
				ObjectInputStream blockedObjectStream = new ObjectInputStream(blockedFileStream);
				blockedMap = (HashMap<String, String>) blockedObjectStream.readObject();
				blockedFileStream.close();
				blockedObjectStream.close();
			}
		} catch (IOException e) {
			System.out.println("IOException");
		} catch (ClassNotFoundException e) {
			System.out.println("ClassNotFoundException");
		}
	}

	
	private void closeServer() {
		System.out.println("Close server");
		running = false;
		try {
			FileOutputStream cachedFileStream = new FileOutputStream("cached.txt");
			ObjectOutputStream cachedObjectStream = new ObjectOutputStream(cachedFileStream);

			cachedObjectStream.writeObject(cachedMap);
			cachedObjectStream.close();
			cachedFileStream.close();

			FileOutputStream blockedFileStream = new FileOutputStream("blocked.txt");
			ObjectOutputStream blockedObjectStream = new ObjectOutputStream(blockedFileStream);
			blockedObjectStream.writeObject(blockedMap);
			blockedObjectStream.close();
			blockedFileStream.close();
			try {
				for (Thread thread : threads) {
					if (thread.isAlive()) {
						thread.join();
					}
				}
			} catch (InterruptedException e) {
				System.out.println("InterruptedException");
			}

		} catch (IOException e) {
			System.out.println("IOException");
		}
		try {
			System.out.println("close connection");
			browserListener.close();
		} catch (Exception e) {
			System.out.println("Exception");
			e.printStackTrace();
		}
	}

	
	@Override
	public void run() {
		Scanner terminalScanner = new Scanner(System.in);
		String userInput;
		while (running) {
			System.out.println("Please enter a command from BLOCKED to see blocked sites, CACHED to see cached site , CLOSE to end this proxy server");
			userInput = terminalScanner.nextLine().toUpperCase();

			switch (userInput) {
				case BLOCKED:
					System.out.println("\nBlocked Sites");
					for (String key : blockedMap.keySet()) {
						System.out.println(key);
					}
					System.out.println();
					break;
				case CACHED:
					System.out.println("\nCached Sites");
					for (String key : cachedMap.keySet()) {
						System.out.println(key);
					}
					System.out.println();
					break;
				case CLOSE:
					running = false;
					closeServer();
					break;
				default:
					blockedMap.put(userInput.toLowerCase(), userInput.toLowerCase());
					System.out.println("\n" + userInput + " blocked successfully \n");
					break;
			}
		}
		terminalScanner.close();
	}
}
