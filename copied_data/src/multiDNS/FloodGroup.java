package multiDNS;

import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.Thread;

public class FloodGroup extends DNSGroup implements Runnable {
	public static final int id = 0;

	protected MultiDNS mdns;
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
	
	FloodGroup(MultiDNS m, String[] nameArgs) {
		// nameArgs[0] is already confirmed to be equal to FloodGroup.id
		// nameArgs[1] will always be the FullName
		// the rest are up to the group!
		super(m, nameArgs[1]);
		serviceHash = new HashMap();
		
		// using default addr/port combo
		if (nameArgs.length < 4) {
			return;
		}
		
		// specify addr/port combo here
		addr = nameArgs[2];
		port = Integer.parseInt(nameArgs[3]);
	}
	
	public void start() {
		// Start server
		thread = new Thread(this);
		thread.start();
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
			
	        System.out.println("Flooding server operating on " + addr + ":" + port);
	        
			while(listening) {
				buf = new byte[1024];
				pack = new DatagramPacket(buf, buf.length);
				
		        socket.receive(pack);
				
				String data = new String(pack.getData());
				String[] args = data.split(":", 2);
				String servicename = args[1].trim();
				
		        System.out.println("Received: " + data + " from: " + pack.getAddress().toString() +
		                           ":" + pack.getPort());
	
				if(args[0].equals("FLD_REQ")) {
					parseReq(servicename, pack.getAddress(), pack.getPort());
				}
				
				pack.setLength(buf.length);
			}
			
			socket.leaveGroup(InetAddress.getByName(addr));
        	socket.close();
        	
 		} catch (Exception e) {
            e.printStackTrace();
        }
	} 
	
	// Generate response to FLD_REQ packets
	private void parseReq(String servicename, InetAddress saddr, int sport) {
		Service service = (Service) serviceHash.get(servicename);
		if (service == null) {
			// we're not responsible for this service
			// (multicast will automatically continue to flood the request)
			// (in non-IP networks there should exist code here to re-broadcast the FLD_REQ)
			return;
		}
		
		try {
			// we have the info for this group so go ahead and reply!
			String bstring = new String("FLD_REP:" + service.name + ":" + service.addr);
			System.out.println("sending: " + bstring);
			byte buf[] = bstring.getBytes();
			DatagramPacket pack = new DatagramPacket(buf, buf.length, saddr, sport);
			socket.send(pack);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// Client-Side when service-resolution requested!
	public String resolveService(String name) {
		String bstring = new String("FLD_REQ:" + name);
		byte buf[] = bstring.getBytes();
		byte buf2[] = new byte[1024];

        DatagramPacket pack2 = new DatagramPacket(buf2, buf2.length);

		try {
			DatagramPacket pack = new DatagramPacket(buf, buf.length, InetAddress.getByName(addr), port);

			MulticastSocket s = new MulticastSocket();
			
			// wait 5 seconds
			s.setSoTimeout(1000);

	        s.send(pack);
			s.receive(pack2);
			// try-catch-exception breaks here
			s.close();

			String data = new String(pack2.getData());
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
			return addr;

		} catch (InterruptedIOException iioe) {
			System.out.println("timeout, service " + name + " not resolved");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
}