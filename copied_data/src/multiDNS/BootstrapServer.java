package multiDNS;

import java.io.*;
import java.net.*;
import java.util.*;

// This class manages the GRP req/rep bootstrapping mechanism
public class BootstrapServer implements Runnable {

	protected MultiDNS mdns;
	
	// Server-side variables
	private MulticastSocket socket;
	private Thread thread;
    public int port = 5858;
    public String addr = "224.1.2.3";
    private boolean listening;
	private String data;
	
	protected static final String token1 = ":";
	protected static final String token2 = "%";
	
	public BootstrapServer(MultiDNS m){
		mdns = m;
		
        // Start server
		thread = new Thread(this);
		thread.start();
	}
	
	// Listens for GRP_REQ packets over multicast
	public void run(){
        byte buf[] = new byte[1024];
        DatagramPacket pack = new DatagramPacket(buf, buf.length);
        listening = true;
		String message;

        try {
	        socket = new MulticastSocket(port);
	        socket.joinGroup(InetAddress.getByName(addr));
	
	        System.out.println("multiDNS server operational on " + addr + ":" + port);
	
			while(listening) {
				message = null;
				
		        socket.receive(pack);
				data = new String(pack.getData());
				System.out.println("BootstrapServer received: " + data + " from: " + pack.getAddress().toString() +
		                           ":" + pack.getPort());
		        
		
				String[] args = data.split(":");
				if (args.length < 1) {
					System.out.println("error: no command");
					continue;
				}
				
				if(args[0].equals("GRP_REQ")) {
					if (args.length < 2) {
						System.out.println("error: no groupname");
						continue;
					}
					message = parseGRP(args[1]);
				} else if (args[0].equals("FIND_GRP")) {
					message = listGroups();
				}
				
				if (message != null) {
					sendMessage(message, pack.getAddress(), pack.getPort());
				}
			}
			
			socket.leaveGroup(InetAddress.getByName(addr));
        	socket.close();
        	
 		} catch (Exception e) {
            e.printStackTrace();
        }
	}
	
	// Generate response to GRP_REQ packets
	private String parseGRP(String groupname) {
		for(DNSGroup group : mdns.dnsGroups()) {
			if (group.name.equals(groupname)) {
				String opts = group.getResponse();
				if (opts != null) {
					// we have the info for this group so go ahead and reply!
					String bstring = new String("GRP_REP" + token2 + group.id + token1 + group.fullName + token1 + opts);
					return bstring;
				}
				return null;
			}
		}
		return null;
	}
	
	private String listGroups() {
		String s = null;
		String opts = null;
		ArrayList entries = new ArrayList();
		// go through the list of groups and get everyone that wants to respond
		for (DNSGroup group : mdns.dnsGroups()) {
			opts = group.getResponse();
			if (opts != null) {
				s = new String(group.id + ":" + group.fullName + ":" + opts);
				entries.add(s);
			}
		}
		
		if (entries.size() == 0) {
			return null;
		}
		
		String response = "GROUPS" + token1 + entries.size() + token2;
		for(Object o : entries.toArray()) {
			String r = (String) o;
			response = response + r + token2;
		}
		
		System.out.println("find groups response: " + response);
		return response;
	}
	
	private void sendMessage(String message, InetAddress daddr, int dport) {
		try {
			byte buf[] = message.getBytes();
			DatagramPacket pack = new DatagramPacket(buf, buf.length, daddr, dport);
			socket.send(pack);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// Client-side code for broadcasting a GRP_REQ packet
	// Returns the instruction-string if received, otherwise "nil"
	public String requestGRP(String gname) {
		String bstring = new String("GRP_REQ" + token2 + gname);
		byte buf[] = bstring.getBytes();
		byte buf2[] = new byte[1024];

        DatagramPacket pack2 = new DatagramPacket(buf2, buf2.length);

		try {
			DatagramPacket pack = new DatagramPacket(buf, buf.length, InetAddress.getByName(addr), port);
	        
			MulticastSocket s = new MulticastSocket();
			s.setSoTimeout(1000);
			
	        s.send(pack);
			s.receive(pack2);
			// try-catch-exception breaks here
			
			System.out.println("discovered group: " + gname);
			s.close();
			
			String data = new String(pack2.getData());
			String[] args = data.split(":", 2);
			String info = args[1].trim();
			if(args[0].equals("GRP_REP")) {
				return info;
			} else {
				// recieved a string but it's incorrect...
				return null;
			}
	
		} catch (InterruptedIOException iioe) {
			System.out.println("requestGRP timed out...");
			return null;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public DNSGroup[] findGroups() {
		String bstring = new String("FIND_GRP" + token1);
		byte buf[] = bstring.getBytes();
		byte buf2[] = new byte[1024];
		ArrayList returnArray = new ArrayList();
		
        DatagramPacket pack2 = new DatagramPacket(buf2, buf2.length);

		try {
			DatagramPacket pack = new DatagramPacket(buf, buf.length, InetAddress.getByName(addr), port);
	        
			MulticastSocket s = new MulticastSocket();
			s.setSoTimeout(1000);
			
	        s.send(pack);
			s.receive(pack2);
			// try-catch-exception breaks here
			s.close();
			
			String data = new String(pack2.getData());
			System.out.println("Data received: " + data);
			
			String[] args = data.split(token2);
			if (args.length < 1) {
				System.out.println("findGroups error: args.length < 1!");
				return null;
			}
			
			String[] params = args[0].split(token1);
			
			if (params.length < 2) {
				System.out.println("findGroups error: params.length = " + params.length + ", expecting value of 2");
				return null;
			}
			if (!params[0].equals("GROUPS")) {
				System.out.println("findGroups error: expecting GROUPS, found " + params[0] + " instead.");
				return null;
			}
			
			int count = Integer.parseInt(params[1]);
			// args.length will be two higher: one for first-entry, one for last (string.split relic)
			if (args.length != count + 2) {
				System.out.println("error: incorrect count! " + args.length + " items found, supposed to be " + count + " items.");
				return null;
			}
			
			// Here we know that we have an appropriate number of "args" content 
			// and that they all correspond to  a DNSGroup. We must now translate
			// each "args" entry to an appropriate DNSGroup object.
			for(int i = 1; i < args.length - 1; i++) {
				// WARNING! look for groups YOU'RE a part of
				// ALSO look out for groups returned multiple times!
				DNSGroup group = DNSGroup.createGroupFromString(mdns, args[i]);
				if (group != null) {
					returnArray.add(group);
				}
			}
			
			DNSGroup[] ret = Arrays.copyOf(returnArray.toArray(), returnArray.toArray().length, DNSGroup[].class);
			return ret;
			
		} catch (InterruptedIOException iioe) {
			System.out.println("findGroups timed out...");
			return null;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public void createGroup() {
		
	}
	
	public void joinGroup() {
		
	}
}