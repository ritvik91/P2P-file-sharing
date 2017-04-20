package node.client;

import index.server.Assign;
import index.server.IndexingServer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;

import node.Peer;
import util.DistributedHashtable;
import util.Util;

public class Client extends Thread {

	private Peer peer;
	private static ArrayList<String> peerList;
	private static ArrayList<String> serverList;
	private ArrayList<Socket> serverSocketList;
	private Socket[] peerSocketList;

	public Client() {	
		try {
			peerList = DistributedHashtable.readConfigFile("peers");
			serverList = DistributedHashtable.readConfigFile("servers");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public Client(Peer peer) {
		this.peer = peer;

		try {
			peerList = DistributedHashtable.readConfigFile("peers");
			serverList = DistributedHashtable.readConfigFile("servers");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// getters
	public Peer getPeer() {
		return this.peer;
	}

	public ArrayList<String> getPeerList() {
		return peerList;
	}

	public ArrayList<Socket> getServerSocketList() {
		return this.serverSocketList;
	}

	public Socket[] getPeerSocketList() {
		return this.peerSocketList;
	}

	// setters
	public void setPeer(Peer peer) {
		this.peer = peer;
	}

	public void setPeerlist() {
		try {
			peerList = DistributedHashtable.readConfigFile("peers");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void setPeerList(ArrayList<String> peerList2) {
		peerList = peerList2;
	}

	public void setServerlist() {
		try {
			serverList = DistributedHashtable.readConfigFile("servers");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void setServerList(ArrayList<String> serverList2) {
		serverList = serverList2;
	}

	public void setServerSocketList(ArrayList<Socket> serverSocketList) {
		this.serverSocketList = serverSocketList;
	}

	public void setPeerSocketList(Socket[] peerSocketList) {
		this.peerSocketList = peerSocketList;
	}

	public void addPeerSocket(Socket peerSocket, int pos) {
		this.peerSocketList[pos] = peerSocket;
	}

	// put
	public Boolean registry(boolean resilience) throws Exception {

		int pId;
		Socket socket;
		boolean ack = false;
		for (String fileName : peer.getFileNames()) {
			pId = DistributedHashtable.hash(fileName, serverList.size());
			pId = resilience ? ((pId == serverList.size() - 1) ? 0 : pId + 1)
					: pId;
			socket = serverSocketList.get(pId);

			//synchronized(socket){
			DataOutputStream dOut = new DataOutputStream(
					socket.getOutputStream());
			// put option
			dOut.writeByte(0);
			dOut.flush();

			// fileName, peer
			dOut.writeUTF(fileName);
			dOut.flush();
			dOut.writeUTF(peer.toString());
			dOut.flush();
			DataInputStream dIn = new DataInputStream(socket.getInputStream());
			ack = dIn.readBoolean();
			if (ack == false)
				break;
			//}
		}

		return ack;
	}

	// get
	@SuppressWarnings("unchecked")
	public ArrayList<Peer> search(String fileName, boolean resilience)
			throws IOException {

		int pId = DistributedHashtable.hash(fileName, serverList.size());
		pId = resilience ? ((pId == serverList.size() - 1) ? 0 : pId + 1) : pId;
		Socket socket = serverSocketList.get(pId);

		//ArrayList<String> resultList = new ArrayList<String>();
		//int numFiles;
		// synchronized(socket){
		DataOutputStream dOut = new DataOutputStream(socket.getOutputStream());

		// lookup option
		dOut.writeByte(1);
		dOut.flush();

		// key
		dOut.writeUTF(fileName);
		dOut.flush();

		//DataInputStream dIn = new DataInputStream(socket.getInputStream());
		/*numFiles = dIn.readInt();
		for (int i = 0; i < numFiles; i++) {
			resultList.add(dIn.readUTF());
		}
		// }*/
		ArrayList<Peer> readList = null;
		ObjectInputStream oin = new ObjectInputStream(socket.getInputStream());
		try {
			readList = (ArrayList<Peer>) oin.readObject();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		

		return readList;
	}

	public long download(String fileName, int peerId, String peer)
			throws Exception {
		String[] peerAddress = peer.split(":");
		Socket socket;
		if (peerSocketList[peerId] == null) {
			socket = new Socket(peerAddress[1],
					Integer.parseInt(peerAddress[2]));
			addPeerSocket(socket, peerId);
		} else {
			socket = getPeerSocketList()[peerId];
		}
		
		//synchronized(socket){
		//System.out.println("here");
		DataOutputStream dOut = new DataOutputStream(socket.getOutputStream());
		// download option
		dOut.writeByte(0);
		dOut.flush();
		dOut.writeUTF(fileName);
		dOut.flush();
		//System.out.println("name writen");

		DataInputStream dIn = new DataInputStream(socket.getInputStream());
		long fileSize = dIn.readLong();
		//System.out.println("fileSize read = " + fileSize);
		String folder = "downloads-peer" + this.peer.getPeerId() + "/";
		File f = new File(folder);
		Boolean created = false;
		if (!f.exists()) {
			try {
				created = f.mkdir();
			} catch (Exception e) {
				System.out
				.println("Couldn't create the folder, the file will be saved in the current directory!");
			}
		} else {
			created = true;
		}

		// if(i != -1) fileName = fileName + i;

		OutputStream out = (created) ? new FileOutputStream(f.toString() + "/"
				+ fileName) : new FileOutputStream(fileName);
		Util.copy(dIn, out, fileSize);
		out.close();
		//System.out.println("File " + fileName + " received from peer " + peerAddress[0] + ":" + peerAddress[1]);
		//}
		return fileSize;
	}

	public boolean startIndexingServer(int port) {
		ServerSocket serverSocket;
		try {
			serverSocket = new ServerSocket(port);
			// start server
			IndexingServer server = new IndexingServer(serverSocket);
			server.start();

			// start assign
			Assign assign = new Assign(server);
			assign.start();
		} catch (IOException e) {
			// e.printStackTrace();
			return false;
		}

		return true;
	}

	public void replicateFiles(int peerId) throws Exception {
		String[] peerAddress = peerList.get(peerId).split(":");
		Socket socket = null;
		if (peerSocketList[peerId] == null) {
			try {
				socket = new Socket(peerAddress[0],
						Integer.parseInt(peerAddress[1]));
			} catch (Exception e) {
				System.out.println("Peer " + peerId + " is down.");
				throw new Exception();
			}
			addPeerSocket(socket, peerId);
		} else {
			socket = getPeerSocketList()[peerId];
		}
		// int id;
		for (String fileName : peer.getFileNames()) {
			// id = DistributedHashtable.hash(file, peerList.size());
			DataOutputStream dOut = new DataOutputStream(
					socket.getOutputStream());
			// download option
			dOut.writeByte(1);
			dOut.flush();
			dOut.writeInt(peer.getPeerId());
			dOut.flush();
			dOut.writeUTF(fileName);
			dOut.flush();
			InputStream in = new FileInputStream(peer.getDirectory() + "/"
					+ fileName);
			long fileSize = new File(peer.getDirectory() + "/" + fileName)
			.length();
			dOut.writeLong(fileSize);
			dOut.flush();
			//System.out.println("fileSize writen");

			Util.copy(in, dOut, fileSize);
			in.close();
			//System.out.println("File " + fileName + " sent for replication");
			DataInputStream dIn = new DataInputStream(socket.getInputStream());
			dIn.readUTF();
			//System.out.println(dIn.readUTF());
			//System.out.println("File sent successfuly");
			//try { Thread.sleep(5); } catch (Exception e) { } 
		}

	}

	public void userInterface() {
		int option, downopt, operations;
		String key = null;
		Boolean result = false;
		ArrayList<Peer> value;

		int id = peer.getPeerId();

		System.out.println("Runnning as Peer " + id);

		Scanner scanner = new Scanner(System.in);
		while (true) {
			System.out.println("\n\nSelect the option:");
			System.out.println("\t1 - Registry");
			System.out.println("\t2 - Search");
			System.out.println("\t3 - Download");
			System.out.println("\t4 - Bench");
			System.out.println("\t5 - Close");

			try {
				option = scanner.nextInt();
				if (option == 1) {
					try {
						result = registry(false);
						result = registry(true);
					} catch (Exception e) {
						System.out.println("Couldn't registry in the system.");
					}

					while(true){
						try {
							replicateFiles(id == peerList.size() - 1 ? 0 : id + 1);
							break;
						} catch (Exception e) {
							e.printStackTrace();
							System.out.println("Cannot replicate, trying again...");
							try { Thread.sleep(5000); } catch (Exception e1) { } 
						}
					}

					System.out.println(result ? "Registered!"
							: "Not registered.");

				} else if (option == 2) {

					System.out.print("File Name: ");
					key = scanner.next();

					try {
						value = search(key, false);
					} catch (Exception e) {
						try {
							value = search(key, true);
						} catch (Exception e1) {
							System.out
							.println("Something went wrong and it couldn'd find the file.");
							continue;
						}
					}
					if (value.size() == 0) {
						System.out.println("File not found in the system.");
						continue;
					}
					for (Peer s : value) {
						String [] res = s.toString().split(":");
						System.out.println("Peer " + res[0] + " (" + res[1] +":"+ res[2] + ") has the file!");
					}

				} else if (option == 3) {

					System.out.print("File Name: ");
					key = scanner.next();

					try {
						value = search(key, false);
					} catch (Exception e) {
						try {
							value = search(key, true);
						} catch (Exception e1) {
							System.out
							.println("Something went wrong and it couldn'd find the file.");
							continue;
						}
					}
					if (value.size() == 0) {
						System.out.println("File not found in the system.");
						continue;
					}
					for (int i = 1; i <= value.size(); i++) {
						String [] res = value.get(i - 1).toString().split(":");
						System.out.println("\t" + i + " - " + "Peer " + res[0] + " (" + res[1] +":"+ res[2] + ")");
						
					}
					while (true) {
						System.out.print("Peer: ");
						downopt = scanner.nextInt();
						if (downopt < 0 || downopt > value.size())
							continue;
						download(key, downopt - 1, value.get(downopt - 1).toString());
						System.out.println("File downloaded!");
						break;
					}

				} else if (option == 4) {
					while (true) {
						System.out.print("Number of operations: ");
						operations = scanner.nextInt();
						if (operations < 0)
							continue;
						bench(operations);
						System.out.println("Benchmarked!");
						break;
					}
				} else if (option == 5) {
					System.out.println("Bye bye!");
					scanner.close();
					return;
				}else {
					System.out.println("Option not valid");
					continue;
				}
			} catch (Exception e) {
				// e.printStackTrace();
				System.out.println("Oops, something went wrong.");
				scanner = new Scanner(System.in);
				// break;
			}
		}
	}

	public void startServers() {
		new Thread() {
			public void run() {
				try {
					peer.server();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}.start();

		new Thread() {
			public void run() {
				try {
					peer.assign();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}.start();
	}

	public void startOpenServer() {
		new Thread() {
			public void run() {
				try {
					peer.openServer();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}.start();
	}
	
	public void bench(int operations){
		long start = System.currentTimeMillis();
		long startTime = start;

		for(int i = 0; i < operations; i++){
			try{
				registry(false);
			} catch (Exception e){
				e.printStackTrace();
			}
		}

		System.out.println("Time for registry peer "
				+ peer.getPeerId() + " " + operations
				+ " times was " + (System.currentTimeMillis() - start) + " ms.");
		
		String fileName;

		ArrayList<Peer> result;
		Random rand = new Random();
		int j = 0;

		start = System.currentTimeMillis();

		for(int i = 0; i < operations; i++){
			//fileName = "file-p" + (j++) + "-0" + i;
			rand.setSeed(System.currentTimeMillis() + j++);
			rand.nextInt(j);
			fileName = "file-p" + rand.nextInt(peerList.size()) + "-0" + rand.nextInt(peer.getNumFiles());
			try {
				search(fileName, false);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		System.out.println("Time for doing " + operations
				+ " searches in peer " + peer.getPeerId() + " was "
				+ (System.currentTimeMillis() - start) + " ms.");

		start = System.currentTimeMillis();

		for(int i = 0; i < operations; i++){
			//fileName = "file-p" + (j++) + "-0" + i;
			rand.setSeed(System.currentTimeMillis() + j++);
			rand.nextInt(j);
			fileName = "file-p" + rand.nextInt(peerList.size()) + "-0" + rand.nextInt(peer.getNumFiles());

			try {
				result = search(fileName, false);
				//System.out.println(client.getPeer().toString() + " " + fileName + " " + result.size());
				if(result.size() == 0){
					i--;
					System.out.println(fileName + " " + result.size());
					continue;
				}
				download(fileName, result.get(0).getPeerId(), result.get(0).toString());
			} catch (Exception e) {
				//e.printStackTrace();
			}
		}
		
		System.out.println("Time for doing " + operations
				+ " downloads in peer " + peer.getPeerId() + " was "
				+ (System.currentTimeMillis() - start) + " ms.");

		System.out.println("Overall Time for doing " + operations
				+ " operations with in peer " + peer.getPeerId() + " was "
				+ (System.currentTimeMillis() - startTime) + " ms.");
	}
	
	public long bench_download(int operations){
		
		long total_bytes = 0;
		String fileName;

		ArrayList<Peer> result;
		Random rand = new Random();
		int j = 0;
		

		for(int i = 0; i < operations; i++){
			//fileName = "file-p" + (j++) + "-0" + i;
			rand.setSeed(System.currentTimeMillis() + j++);
			rand.nextInt(j);
			fileName = "file-p" + rand.nextInt(peerList.size()) + "-0" + rand.nextInt(peer.getNumFiles());

			try {
				result = search(fileName, false);
				//System.out.println(fileName + " " + result.size());
				if(result.size() == 0){
					System.out.println(fileName + " " + result.size());
					i--;
					continue;
				}
				total_bytes += download(fileName, result.get(0).getPeerId(), result.get(0).toString());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		
		
		return total_bytes;
	}

	public static void main(String[] args) throws IOException {

		Client c = new Client();

		if (args.length < 1) {
			// System.out.println("Usage: java -jar build/Client.jar <PeerId> <Address> <Port> <Folder>");
			System.out.println("Usage: java -jar build/Client.jar <PeerId>");
			return;
		}

		int id = 0;
		try {
			id = Integer.parseInt(args[0]);
		} catch (Exception e) {
			System.out.println("Put a valid Id");
			return;
		}

		if (id > peerList.size()) {
			System.out
			.println("Peer Id shouldn't be greater than the number provided in the config file!");
			return;
		}

		String[] peerAddress;
		peerAddress = peerList.get(id).split(":");
		String address = peerAddress[0];
		int port = Integer.parseInt(peerAddress[1]);

		// String dir = args[3];
		String dir = peerAddress[2];
		File folder = new File(dir);

		if (!folder.isDirectory()) {
			System.out.println("Put a valid directory name");
			return;
		}

		ArrayList<String> fileNames = Util.listFilesForFolder(folder);
		final Peer peer = new Peer(id, address, port, dir, fileNames,
				fileNames.size());

		String[] serverAddress;

		ArrayList<Socket> serverSocketList = new ArrayList<Socket>();


		c.setPeer(peer);
		// startServers();
		c.startOpenServer();

		int i;
		// checking if all are open
		for (i = 0; i < serverList.size(); i++) {
			serverAddress = serverList.get(i).split(":");
			address = serverAddress[0];
			port = Integer.parseInt(serverAddress[1]);

			try {
				System.out.println("Testing connection to server " + address
						+ ":" + port);
				Socket s = new Socket(address, port);
				serverSocketList.add(s);
				System.out.println("Server " + address + ":" + port
						+ " is running.");
			} catch (Exception e) {
				// System.out.println("Not connected to server " + address + ":"
				// + port);
				// System.out.println("Trying again");
				i--;
				port--;
			}
		}
		c.setServerSocketList(serverSocketList);

		c.setPeerSocketList(new Socket[peerList.size()]);

		c.userInterface();
		System.exit(1);
	}

}
