import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Server implements Runnable {
	private List<ServerClient> clients = new ArrayList<ServerClient>();
	private List<Integer> clientResponse = new ArrayList<Integer>();
	private final int MAX_ATTEMPTS = 5;

	private DatagramSocket socket;
	private int port;
	
	private boolean running = false; 
	private Thread run, send, receive, manage; 

	public Server (int port) {
		this.port = port;
		try {
			socket = new DatagramSocket(port);
		} catch (SocketException e) {
			e.printStackTrace();
		}
		running = true; 
		run = new Thread(this, "Running");
		run.start(); 
	}
	
	public void run() {
		console("Server started on port " + port);
		manageClients(); 
		receive(); 
		Scanner scanner = new Scanner(System.in); 
		while (running) { 
			String text = scanner.nextLine(); 
			if (text.equals("/quit")) {
				text = "Server has closed./e/";
				sendToAll(text, -1); 
				for (int i = 0; i < clients.size(); i++) {
					disconnect(clients.get(i).getID(), true);
				}
				close();
				System.exit(0);
			} else {
				text = "Server: " + text + "/e/"; 
				sendToAll(text, -1); 
			}
		}
	}

	private void manageClients() {
		manage = new Thread("Manage") {
			public void run() {
				while (running) {
					sendToAll("/p/server", -1);
					try {
						Thread.sleep(2000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					for (int i = 0; i < clients.size(); i++) {
						ServerClient c = clients.get(i);
						if (!clientResponse.contains(c.getID())) {
							if (c.attempt >= MAX_ATTEMPTS) {
								disconnect(c.getID(), false);
							} else {
								c.attempt++;
							}
						} else {
							clientResponse.remove(new Integer(c.getID()));
							c.attempt = 0;
						}
					}
				}
			}
		};
		manage.start();
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
					try {
						process(packet);
					} catch (IOException e) {
						e.printStackTrace();
					} 
				}
			}
		};
		receive.start(); 
	}
	
	private void sendToAll(String message, int id) {
		if (!message.startsWith("/p/")) console(message.split("/e/")[0]); 
		for (int i = 0; i < clients.size(); i++) { 
			ServerClient client = clients.get(i);  
			if (client.getID() != id){ 
				send(message.getBytes(), client.getAddress(), client.getPort()); 
			}
		}
	}

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
	
	private void send(String message, InetAddress address, int port) {
		message += "/e/"; 
		send(message.getBytes(), address, port); 
	}
	
	/**
	This is the processing method that will go through every message sent form the clients, and split them into categories. each category will be used to perform different things such as:
	Connecting the client to the server:
		- This is denoted by the /c/ tag at the front of the message
		- This is needed so that the server will be able to assign that client an ID (Unique Identifier) and store its credentials, like it's name, IP address, and Port number.
	Sending a message to everyone:
		- This is denoted by the /m/ tag at the front of the message
		- This is needed so that the server can see that it is an ordinary message sent by a client, and will be able to send that message to everyone else.
	Disconnecting the client from the server:
		- This is denoted by the /d/ tag at the front of the message
		- This is needed so that the server can be able to disconnect the client that is wanting to close the connection.
	Identifying an ID:
		- This is denoted by the /i/ tag, in the middle of the message.
		- This is needed so that the server will know who sent the message and be able to sent it to everyone but that client. You may wonder why not just use the name as the Identifier or the port, or even the address, this is because people might have the same name, they might come from the same port number but on different computers, or even come from the same network. This will make it easier to keep track of clients.
		- This is also used so that the server knows who to disconnect when the client wants to disconnect.
	Receiving a Ping response:
		- This is denoted by the /p/ tag at the front of the message.
		- This will allow the server to know that the user is still connected with it.
	List of files:
		- This is denoted by the /l/ tag at the front of the message.
		- This is used to separate each variable in the list.
		- This will allow the server to easily split the message and write it into a text file.
	Querying a song:
		- This is denoted by the /q/ tag at the start of the message.
		- This is used to tell the server that the client wants to know who has this song, and respond with a list of users that have that specific song.
	P2P Transfer:
		- This is denoted by the /t/ tag at the start of the message.
		- This will be used by the client to get another client to send a file to the first, sending the name of the song to the given address.
	End:
		- This is denoted by the /e/ tag at the end of the message.
		- This is needed so that the server and the client can know when to stop the string. Without this there will be empty spaces showing up in the console. Alternatively \n or \r could be used.
	**/
	private void process(DatagramPacket packet) throws IOException {
		String text = new String(packet.getData());
		String message = text.substring(3, text.length());
		if (text.startsWith("/c/")) { 
			int id =  UniqueIdentifier.getIdentifier(); 
			message = message.split("/e/")[0]; 
			clients.add(new ServerClient(message, packet.getAddress(), packet.getPort(), id)); 
			message = "Successfully connected to server! " + message + " From IP: " + packet.getAddress() + ":" + packet.getPort() + "/e/";
			send("/i/" + String.valueOf(id), packet.getAddress(), packet.getPort()); 
			sendToAll(message, -1); 
		} else if (text.startsWith("/m/")) {
			int ID = Integer.parseInt(message.split("/i/|/e/")[1]);
			message = message.split("/i/|/e/")[0];
			sendToAll(message + "/e/", ID); 
		} else if (text.startsWith("/d/")) { 
			String ID = message.split("/e/")[0]; 
			disconnect(Integer.parseInt(ID), true); 
		} else if (text.startsWith("/q/")) {
			int ID = Integer.parseInt(message.split("/i/|/e/")[1]); 
			message = message.split("/i/|/e/")[0]; 
			message = search(message, ID);
			send(message, packet.getAddress(), packet.getPort());
		} else if (text.startsWith("/p/")) { 
			int ID = Integer.parseInt(message.split("/i/|/e/")[1]); 
			clientResponse.add(ID);
			message = message.split("/i/|/e/")[0];
			String[] list = message.split("/l/");
			BufferedWriter bw = new BufferedWriter(new FileWriter(ID + ".txt"));
			for (int i = 0; i < list.length; i++) {
				bw.write(list[i] + "\n");
			}
			bw.close();
		} else if (text.startsWith("/t/")) {
			String song = "/t/" + message.split("/t/|/e/")[0] + "/t/" + packet.getAddress() + "/t/" + packet.getPort();
			InetAddress IP = InetAddress.getByName(message.split("/t/|/e/")[1].split(":")[0]);
			int p = Integer.parseInt(message.split("/t/|/e/")[1].split(":")[1]);
			send(song, IP, p);
		} else {
			console(message); 
		}
	}
	
	public String search(String song, int id) throws IOException{
		String songs = "";
		String text = "";
		BufferedReader Br = new BufferedReader(new FileReader(String.valueOf(id) + ".txt"));
		while ((text = Br.readLine()) != null) songs += text + "/s/";
		Br.close();
		String[] list = songs.split("/s/");
		for (int j = 0; j < list.length; j++) {
			if (list[j].equals(song)) {
				return "You already have the song, ya dunce!";
			}
		}
		text = "";
		for (int i = 0; i < clients.size(); i++) {
			BufferedReader br = new BufferedReader(new FileReader(String.valueOf(clients.get(i).getID()) + ".txt"));
			songs = "";
			String temp;
			while ((temp = br.readLine()) != null) songs += temp + "/s/";
			br.close();
			list = songs.split("/s/");
			for (int j = 0; j < list.length; j++) {
				if (list[j].equals(song)) {
					text += clients.get(i).getAddress().toString() + ":" + clients.get(i).getPort() + "\n";
				}
			}
		}
		return text + "/e/";
	}

	private void disconnect(int id, boolean status) {
		ServerClient c = null; 
		for (int i = 0; i < clients.size(); i++) { 
			if (clients.get(i).getID() == id) { 
				c = clients.get(i); 
				UniqueIdentifier.addIdentifier(c.getID()); 
				File file = new File(String.valueOf(clients.get(i).getID()) + ".txt");
				try {
					file.delete();
				} catch (Exception e) {
					e.printStackTrace();
				}
				clients.remove(i); 
				break; 
			}
		}
		String message = "";
		if (status) message = "Client " + c.name + " (" + c.getID() + ") @ " + c.address.toString() + ":" + c.port + " disconnected./e/";
		else message = "Client " + c.name + " (" + c.getID() + ") @ " + c.address.toString() + ":" + c.port + " timed out./e/";
		sendToAll(message, -1);
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
	
	public void console(String message) {
		System.out.println(message); 
	}

	public static void main(String[] args) { 
		int port;
		if (args.length != 1) {
			System.out.println("Usage: java -jar Program.jar [port]");
			return;
		}
		port = Integer.parseInt(args[0]);
		new Server(port);
	}
}
