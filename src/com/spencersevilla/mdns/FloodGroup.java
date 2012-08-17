package com.spencersevilla.mdns;

import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.Thread;

public class FloodGroup extends DNSGroup implements Runnable {
	public static final int id = 0;

	public boolean respond;
	public String response;  
	private HashMap serviceHash;
	private Thread thread;
	public int port = 6363;
    public String addr = "224.4.5.6";
    private MulticastSocket socket;

	// Constructor and Bookkeeping Methods
	FloodGroup(MultiDNS m, String n) {
		super(m, n);

		serviceHash = new HashMap();
	}
	
	FloodGroup(MultiDNS m, ArrayList<String> nameArgs) {
		// nameArgs[0] will always be the fullName

		super(m, nameArgs.get(0));
		serviceHash = new HashMap();
		
		if (nameArgs.size() > 1) {
			addr = nameArgs.get(1);
		}
		
		if (nameArgs.size() > 2) {
			port = Integer.parseInt(nameArgs.get(2));
		}
	}
	
	public void start() {
		// Start server
		thread = new Thread(this);
		thread.start();
	}
	
	public void stop() {
		System.out.println("FG " + fullName + " stopping.");
	}

	public String getResponse() {
		String s = addr + ":" + port;
		return s;
	}
	
	// Interface with MultiDNS
	public boolean createSubGroup(String name) {
		return true;
	}
	
	public void serviceRegistered(Service s) {
		serviceHash.put(s.name, s);
	}
	
	public void serviceRemoved(Service s) {		
		serviceHash.remove(s.name);
	}
	
	// Startup server to respond to requests!
	public void run() {
        byte buf[];
        DatagramPacket pack;
        boolean listening = true;

        try {
			socket = new MulticastSocket(port);
	        socket.joinGroup(InetAddress.getByName(addr));
			
	        System.out.println("FG " + fullName + ": serving on " + addr + ":" + port);
	        
			while(listening) {
				buf = new byte[1024];
				pack = new DatagramPacket(buf, buf.length);
				
		        socket.receive(pack);
				
				String data = new String(pack.getData());
				String[] args = data.split(":");
				System.out.println("FG " + fullName + ": received " + data);
		        
				if (args.length < 3) {
					System.err.println("FG " + fullName + ": incorrect number of args!");
					continue;
				}
								
				if(!args[0].equals("FLD_REQ")) {
					System.err.println("FG " + fullName + ": incorrect command!");
					continue;
				}
				
				if (!args[1].equals(fullName)) {
					System.err.println("FG " + fullName + ": should not have received this request!");
					continue;
				}
				
				String servicename = args[2].trim();
				parseReq(servicename, pack.getAddress(), pack.getPort());
			}
			
			socket.leaveGroup(InetAddress.getByName(addr));
        	socket.close();
        	
 		} catch (Exception e) {
            e.printStackTrace();
        }
	} 
	
	// Generate response to FLD_REQ packets
	private void parseReq(String servicename, InetAddress saddr, int sport) {
		String addr = null;
		
		if (isBestPossibleMatch(servicename)) {
			// already in the best group. here, either we answer the req (if we can)
			// or it continues to flood the service group, timing out if nonexistent
			String[] stringArray = servicename.split("\\.");
			String name = stringArray[0];
			Service service = (Service) serviceHash.get(name);
			if (service != null) {
				addr = service.addr;
			}
		} else {
			// here let's see if we can find a better-matching group? if so, then it's
			// guaranteed that no-one in THIS group will respond so we should forward it
			int score = calculateScore(servicename);
			InetAddress a = mdns.forwardRequest(servicename, score);
			addr = a.getHostAddress();
		}
		
		if (addr == null) {
			// we can't find this string OR we're not responsible for it
			System.out.println("FG " + fullName + ": ignoring request");
			return;
		}
		
		try {
			// we have the info for this group so go ahead and reply!
			// note: the response we give uses the string they requested!
			String bstring = new String("FLD_REP:" + servicename + ":" + addr);
			System.out.println("FG " + fullName + ": sending " + bstring);
			byte buf[] = bstring.getBytes();
			DatagramPacket pack = new DatagramPacket(buf, buf.length, saddr, sport);
			socket.send(pack);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// Client-Side when service-resolution requested!
	public InetAddress resolveService(String name) {
		return resolveService(name, 0);
	}
	
	public InetAddress resolveService(String name, int minScore) {
		String bstring = new String("FLD_REQ:" + fullName + ":" + name);
		byte buf[] = bstring.getBytes();
		byte buf2[] = new byte[1024];

        DatagramPacket pack2 = new DatagramPacket(buf2, buf2.length);

		try {
			DatagramPacket pack = new DatagramPacket(buf, buf.length, InetAddress.getByName(addr), port);

			MulticastSocket s = new MulticastSocket();
			
			// wait 10 seconds
			s.setSoTimeout(10000);

	        s.send(pack);
			s.receive(pack2);
			// try-catch-exception breaks here
			s.close();
			
			String data = new String(pack2.getData());
			System.out.println("FG " + fullName + ": received " + data);
			
			String[] args = data.split(":", 3);
			String addr = args[2].trim();
			if(!args[0].equals("FLD_REP")) {
				// recieved a string but it's incorrect...
				return null;
			}
			if (!args[1].equals(name)) {
				// wrong service?
				return null;
			}

			InetAddress result = InetAddress.getByName(addr);
			return result;
		} catch (InterruptedIOException iioe) {
			System.out.println("FG " + fullName + ": timeout, service " + name + " not resolved");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
}
