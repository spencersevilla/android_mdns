package multiDNS;

import java.io.*;
import java.net.*;
import java.util.*;

public class IPCServer {
	protected MultiDNS mdns;
	private Thread thread;
	public int port = 53;
    private DatagramSocket socket;
	private boolean listening;
    
	// Constructor and Bookkeeping Methods
	IPCServer(MultiDNS m) {
		mdns = m;
		
		// Start server
		serve();
	}
	
	private void serve() {
        byte buf[];
		byte buf2[];
        DatagramPacket pack;
		DatagramPacket pack2;

        try {
	        socket = new DatagramSocket(port);
			listening = true;

			while(listening) {
				buf = new byte[1024];
				pack = new DatagramPacket(buf, buf.length);
				
		        socket.receive(pack);
				String data = new String(pack.getData());
				String[] args = data.split(":", 2);
				
				System.out.println("RECEIVED SOMETHING: " + data);
				System.out.println("FROM: " + pack.getAddress() + ":" + pack.getPort());
				if (args.length < 2) {
					continue;
				}
				
				String response;
				String name = args[1].trim();
				
				pack.setLength(buf.length);
				
				if(args[0].equals("RESOLVE")) {
					response = "RESPONSE:" + mdns.resolveService(name);
				} else if (args[0].equals("REGISTER")) {
					mdns.createService(name);
					// indicate success or failure?
					response = "RESPONSE:SUCCESS";
				} else {
					// invalid string!
					continue;
				}
				// send-back response string here
				buf2 = response.getBytes();
				pack2 = new DatagramPacket(buf2, buf2.length, pack.getAddress(), pack.getPort());
				socket.send(pack2);
			}
        	socket.close();

 		} catch (Exception e) {
            e.printStackTrace();
        }
	}
}