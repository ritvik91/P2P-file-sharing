package bench.local;

import node.client.Client;

public class Throughput extends Thread {
	private Client client;
	private int operations;

	public Throughput(Client client, int operations) {
		this.client = client;
		this.operations = operations;
	}

	public void run() {
		
		
		try {
			long bytes;
			client.registry(false);
			System.out.println("Registered!");
			
			long start = System.currentTimeMillis();
			
			bytes = client.bench_download(operations);
			
			long time = (System.currentTimeMillis() - start); 
			
			System.out.println("Time for doing " + operations
					+ " downloads in peer " + client.getPeer().getPeerId() + " was "
					+ time + " ms.");
			
			System.out.println("Thoughput of peer " +  + client.getPeer().getPeerId() + " = " + bytes/(time/1000) + " bytes/s.");
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
}
