package node.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

import util.Util;

public class Server extends Thread {

	private String directory;
	private Socket socket;

	public Server(Socket socket, String directory) {
		this.directory = directory;
		this.socket = socket;
	}

	public void run() {
		try {
			//System.out.println("here");	
			DataInputStream dIn = new DataInputStream(socket.getInputStream());
			String fileName = dIn.readUTF();
			//System.out.println("fileName read " + fileName);
			InputStream in = new FileInputStream(directory + "/" + fileName);
			long fileSize = new File(directory + "/" + fileName).length();
			DataOutputStream dOut = new DataOutputStream(socket.getOutputStream());
			dOut.writeLong(fileSize);
			dOut.flush();
			//System.out.println("fileSize writen");

			Util.copy(in, dOut, fileSize);
			in.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

	}
}
