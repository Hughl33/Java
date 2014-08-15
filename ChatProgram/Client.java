import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Scanner;

public class Client implements Runnable{
	private String name;
	private int port;
	
	private DatagramSocket socket; 
	private InetAddress ip;
	private int ID = -1; 
	
	private Thread run, send, receive, transfer; 
	private boolean running = false; 
	
	private int size = 20022386;
    private String requestedSong;
	
	public Client(String name, String address, int port) {
		this.name = name;
		this.port = port;
		
		boolean connect = openConnection(address);
		if (!connect) {
			console("Connection Failed!");
		} else {
			console("Connection Success!");
			String connection = "/c/" + name + "/e/";
			send(connection.getBytes());
		}
		
		running = true;
		run = new Thread(this, "Running");
		run.start(); 
	}
	
	public void run() {
		receive();
		Scanner scanner = new Scanner(System.in); 
		while (running) { 
			String text = scanner.nextLine();
			send(text, ID);
		}
	}
	
	public boolean openConnection(String address) {
		try { 
			socket = new DatagramSocket();
			ip = InetAddress.getByName(address);
		} catch (UnknownHostException e) { 
			e.printStackTrace();
			return false;
		} catch (SocketException e) { 
			e.printStackTrace();
			return false;
		}
		return true; 
	}
	
	public void receive() {
		receive = new Thread("Receive"){
			public void run () {
				while(running){
					byte[] data = new byte[1024];
					DatagramPacket packet = new DatagramPacket(data, data.length);
					try {
						socket.receive(packet); 
					} catch (IOException e) { 
						e.printStackTrace();
					}
					process(packet); 
				}
			}
		};
		receive.start(); 
	}
	
	public void send(final byte[] data) { 
		send = new Thread("Send"){ 
			public void run () {
				DatagramPacket packet = new DatagramPacket(data, data.length, ip, port); 
				try { 
					socket.send(packet); 
				} catch (IOException e) { 
					e.printStackTrace();
				}
			}
		};
		send.start(); 
	}
	
	/**
	Chat Commands/Functions:
		- These are denoted by a single '/'.
		- '/get': This command is used to get to request a file from another user. It will be prompting you to enter in the Address and port number of the selected user, and the name of the file that you are looking for.
		- '/search': This command is used to search for who (retrieving their IP and Port) has a specific file. This will be given to the server and the server will give a list of those who have this song.
		- '/quit': This command is used to essentially quit the server. Telling the server that it is disconnecting. The server will then remove it from its list, notify everyone else.
		- '/help': This command will show all available commands and what they do.
		- If the user tries to use an unimplemented command it will be sent as an ordinary message to everyone. 
	**/
	private void send(String message, int id) {
		if (message.equals("")) {
			return; 
		} else if (message.equals("/get")) {
			console("Address:Port ->");
			Scanner scanner = new Scanner(System.in);
			String IP = scanner.nextLine();
			console("Song: (.mp3)");
			requestedSong = scanner.nextLine();
			message = "/t/" + requestedSong + "/t/" + IP + "/e/";
			send(message.getBytes());
		} else if (message.equals("/search")) {
			query();
		} else if (message.equals("/quit")) { 
			message = "/d/" + id + "/e/";
			send(message.getBytes());
			running = false; 
			close(); 
			System.exit(0); 
			} else if (message.equals("/help")) {
			String text = "List of Available commands:\n" +
							"/get - To get a song.\n" + 
							"/search - To search of songs.\n" +
							"/quit - To quit the program.\n";
			console(text);
			return;
		} else if (message.startsWith("/p/")) {
			message += "/i/" + id + "/e/";
			send(message.getBytes()); 
		} else if (message.startsWith("/q/")) {
			message += "/i/" + id + "/e/";
			send(message.getBytes()); 
		} else {
			message = name + ": " + message; 
			message = "/m/" + message + "/i/" + id + "/e/"; 
			send(message.getBytes()); 
		}
	}
	
	private void process(DatagramPacket packet) {
		String message = new String(packet.getData()); 
		message = message.split("/e/")[0]; 
		if (message.startsWith("/i/")) { 
			message = message.substring(3, message.length()); 
			ID = Integer.parseInt(message); 
			} else if (message.startsWith("/p/")) {
			message = "/p/" + list();
			send(message, ID);
		} else if (message.startsWith("/q/")) {
			message = message.substring(3, message.length());
			console(message);
		} else if (message.startsWith("/t/")) { 
			requestedSong = message.substring(3, message.length()).split("/t/")[0];
			InetAddress ip = null;
			String temp = message.substring(3, message.length()).split("/t/")[1];
			temp = temp.substring(1, temp.length());
			try {
				ip = InetAddress.getByName(temp);
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
			int p = Integer.parseInt(message.substring(3, message.length()).split("/t/")[2].split("/e/")[0]);
			transfer();
			send(("/r/").getBytes(), ip, p);
		} else if (message.startsWith("/r/")) {
			message = packet.getAddress().toString().substring(1,this.ip.toString().length());
			try {
				request(message);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			console(message); 
		}
	}
	
	public String list() {
		String list = "";
		File[] files = new File("songs").listFiles();
		for (int i = 0; i < files.length; i++) {
			list += files[i].getName() + "/l/";
		}
		return list;
	}
	
	public void query() {
		String text = "What song are you looking for?";
		console(text);
		Scanner scanner = new Scanner(System.in);
		text = "/q/";
		text += scanner.nextLine(); 
		send(text, ID); 
	}
	
	public void close() {
		new Thread() { 
			public void run() {
				synchronized (socket) { //
					socket.close();
				}
			}
		}.start();
	}
	
	/**
	==============================================================================================================================
	P2P TCP File Transfer methods
	==============================================================================================================================
	**/
	
	private void send(final byte[] data, final InetAddress address, final int port) { 
		send = new Thread("Send") { 
			public void run() { 
				DatagramPacket packet = new DatagramPacket(data, data.length, address, port); 
				try { 
					socket.send(packet); 
				} catch (IOException e) { 
					e.printStackTrace();
				}
			}
		};
		send.start(); 
	}
	
	public void transfer() {
		transfer = new Thread("Transfer") {
			public void run() {
				FileInputStream fis = null;
			    BufferedInputStream bis = null;
			    OutputStream os = null;
			    ServerSocket server = null;
			    Socket socket = null;
			    try {
			      server = new ServerSocket(4001);
			      console("Waiting...");
			      socket = server.accept();
			      
			      console("Accepted connection : " + socket);
			      File myFile = new File ("songs/" + requestedSong);
			      byte [] bytes  = new byte [(int)myFile.length()];
			      fis = new FileInputStream(myFile);
			      bis = new BufferedInputStream(fis);
			      bis.read(bytes,0,bytes.length);
			      os = socket.getOutputStream();
			      console("Sending " + requestedSong + "(" + bytes.length + " bytes)");
			      os.write(bytes,0,bytes.length);
			      os.flush();
			      console("Done.");
			      
			      if (bis != null) bis.close();
			      if (os != null) os.close();
			      if (socket != null) socket.close();
			      if (server != null) server.close();
			    } catch(IOException e) {
			    	e.printStackTrace();
			    }
			}
		};
		transfer.start();
	}
	
	public void request(String ip) throws IOException{
		int bytesRead;
	    int current = 0;
	    FileOutputStream fos = null;
	    BufferedOutputStream bos = null;
	    Socket socket = null;
	    try {
	      socket = new Socket(ip, 4001);
	      console("Connecting...");

	      byte [] bytes  = new byte [size];
	      InputStream is = socket.getInputStream();
	      fos = new FileOutputStream("songs/" + requestedSong);
	      bos = new BufferedOutputStream(fos);
	      bytesRead = is.read(bytes,0,bytes.length);
	      current = bytesRead;

	      do {
	         bytesRead = is.read(bytes, current, (bytes.length-current));
	         if(bytesRead >= 0) current += bytesRead;
	      } while(bytesRead > -1);

	      bos.write(bytes, 0 , current);
	      bos.flush();
	      console("File " + requestedSong + " downloaded (" + current + " bytes read)");
	    }
	    finally {
	      if (fos != null) fos.close();
	      if (bos != null) bos.close();
	      if (socket != null) socket.close();
	    }
	}
	
	public void console(String message) {
		System.out.println(message);
	}

	public static void main(String[] args) {
		String name, address;
		int port;
		
		if (args.length != 3) {
			System.out.println("Usage: java -jar Program.jar [Name] [IP Address] [Port]");
			return;
		}
		name = args[0];
		address = args[1];
		port = Integer.parseInt(args[2]);
		
		new Client(name, address, port);
	}
}
