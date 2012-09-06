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
    private ArrayList<InetAddress> self_addrs;

    private InetAddress parent_addr;
    private String parent_name; 

	// Constructor and Bookkeeping Methods
	FloodGroup(MultiDNS m, String n) {
		super(m, n);
		self_addrs = new ArrayList<InetAddress>();
		serviceHash = new HashMap();
		parent_addr = null;
		parent_name = null;
	}
	
	FloodGroup(MultiDNS m, ArrayList<String> nameArgs) {
		// nameArgs[0] will always be the fullName

		super(m, nameArgs.get(0));
		serviceHash = new HashMap();
		self_addrs = new ArrayList<InetAddress>();
		parent_addr = null;
		parent_name = null;

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

        // code to record ALL self-ip addresses so we can ignore them!
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
					self_addrs.add(enumIpAddr.nextElement());
				}
			}
		} catch (SocketException e) {
			System.out.println(" (error retrieving network interface list)");
		}

		// only findParent if we aren't top-level and we don't have a direct link
		int index = fullName.indexOf(".");
		if (index != -1 && index + 1 < fullName.length() && parent == null) {
			parent_name = fullName.substring(index + 1);
			findParent();
		}

        try {
			socket = new MulticastSocket(port);
	        socket.joinGroup(InetAddress.getByName(addr));

	        System.out.println("FG " + fullName + ": serving on " + addr + ":" + port);
	        
			while(listening) {
				buf = new byte[1024];
				pack = new DatagramPacket(buf, buf.length);
				
		        socket.receive(pack);

		        // FIRST: check to make sure that we aren't asking ourself!
		        if (self_addrs.contains(pack.getAddress())) {
		        	continue;
		        }
		        				
				String data = new String(pack.getData());
				String[] args = data.split(":");
				System.out.println("FG " + fullName + ": received " + data);
		        
		        if (args.length < 2) {
		        	System.err.println("FG " + fullName + ": incorrect number of args!");
		        	continue;
		        }

		        String name = args[1].trim();
				if (!name.equals(fullName)) {
					System.err.println("FG " + fullName + ": should not have received this request: " + args[1]);
					continue;
				}

				if (args[0].equals("PARENT_REQ")) {
					parentReq(args, pack.getAddress(), pack.getPort());

				} else if(args[0].equals("FLD_REQ")) {
					parseReq(args, pack.getAddress(), pack.getPort());

				} else {
					System.err.println("FG " + fullName + ": incorrect command!");
					continue;
				}
				
			}
			
			socket.leaveGroup(InetAddress.getByName(addr));
        	socket.close();
        	
 		} catch (Exception e) {
            e.printStackTrace();
        }
	} 
	
	private void parentReq(String[] args, InetAddress saddr, int sport) {
		// issue a response message if we are also a member of the parent
		// group! (thus requests can be unicast to us...)
		if (parent == null) {
			return;
		}

		// we can talk to the parent group, so go ahead and reply!
		try {
			String bstring = new String("PARENT_REP:" + fullName);
			System.out.println("FG " + fullName + ": sending " + bstring);
			byte buf[] = bstring.getBytes();
			DatagramPacket pack = new DatagramPacket(buf, buf.length, saddr, sport);
			socket.send(pack);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// Generate response to FLD_REQ packets
	private void parseReq(String[] args, InetAddress saddr, int sport) {
		InetAddress addr = null;

		if (args.length < 3) {
			System.err.println("FG " + fullName + ": incorrect number of args!");
			return;
		}
		String servicename = args[2].trim();
		
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
			addr = mdns.forwardRequest(servicename, this);
		}
		
		if (addr == null) {
			// we can't find this string OR we're not responsible for it
			System.out.println("FG " + fullName + ": ignoring request");
			return;
		}
		
		try {
			// we have the info for this group so go ahead and reply!
			// note: the response we give uses the string they requested!
			String bstring = new String("FLD_REP:" + servicename + ":" + addr.getHostAddress());
			System.out.println("FG " + fullName + ": sending " + bstring);
			byte buf[] = bstring.getBytes();
			DatagramPacket pack = new DatagramPacket(buf, buf.length, saddr, sport);
			socket.send(pack);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void findParent() {
		System.out.println("FG " + fullName + ": searching for parent");
		String bstring = new String("PARENT_REQ:" + fullName);
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
			
			String[] args = data.split(":");

			if (args.length < 2) {
				// received not enough info...
				System.err.println("FG " + fullName + " getParent error: not enough args!");
				return;
			}

			if(!args[0].equals("PARENT_REP")) {
				// recieved a string but it's not what we want...
				System.err.println("FG " + fullName + " getParent error: incorrect command!");
				return;
			}

			String name = args[1].trim();
			if (!name.equals(name)) {
				System.err.println("FG " + fullName + " getParent error: incorrect groupname!");
				return;
			}

			parent_addr = pack2.getAddress();
			System.out.println("FG " + fullName + ": found parent at " + parent_addr);
			return;
		} catch (InterruptedIOException iioe) {
			System.out.println("FG " + fullName + ": timeout, parent not found?");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return;
	}

	private boolean chooseParent(String servicename) {
		if (parent_addr == null || parent_name == null) {
			System.out.println("chooseParent error");
			return false;
		}

		if (parent != null) {
			System.out.println("WHAT? Why do we have a parent and parent_addr?");
			return false;
		}

		return (DNSGroup.calculateScore(servicename, parent_name) >= calculateScore(servicename));
	}

	// Client-Side when service-resolution requested!
	public InetAddress resolveService(String name) {
		return resolveService(name, 0);
	}

	public InetAddress resolveService(String name, int minScore) {
		System.out.println("FG " + fullName + ": resolving " + name);

		if (chooseParent(name)) {
			return mdns.forwardRequest(name, this, parent_addr, 53);
		}

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
