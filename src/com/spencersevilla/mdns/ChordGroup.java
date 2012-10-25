package com.spencersevilla.mdns;

import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.Thread;

// possibly trim this down?
import de.uniba.wiai.lspi.chord.data.URL;
import de.uniba.wiai.lspi.chord.service.Key;
import de.uniba.wiai.lspi.chord.service.ServiceException;
import de.uniba.wiai.lspi.chord.service.impl.ChordImpl;
import de.uniba.wiai.lspi.chord.service.Chord;

// implement DNSGroup, Runnable later on
public class ChordGroup extends DNSGroup implements Runnable {
	public static int id = 1;
	private Thread thread;
	private Chord chord;
	private boolean running;
	public InetAddress laddr = null;
	public int lport = 0;
	public InetAddress daddr = null;
	public int dport = 0;
	private ArrayList<Service> services;

	ChordGroup(MultiDNS m, String n) {
		super(m, n);
		services = new ArrayList<Service>();
	}
	
	ChordGroup(MultiDNS m, ArrayList<String> nameArgs) {
		super(m, nameArgs.get(0));
		services = new ArrayList<Service>();

		if (nameArgs.get(1).equals("create")) {
			if (nameArgs.size() < 4) {
				System.err.println("CG " + fullName + " init error: invalid init string!");
				return;
			}

			try {
				laddr = InetAddress.getByName(nameArgs.get(2));
			} catch (Exception e) {
				System.err.println("CG " + fullName + " init error: invalid address!");
				return;
			}

			lport = Integer.parseInt(nameArgs.get(3));

		} else if (nameArgs.get(1).equals("join")) {
			if (nameArgs.size() < 6) {
				System.err.println("CG " + fullName + " init error: invalid init string!");
				return;
			}

			try {
				daddr = InetAddress.getByName(nameArgs.get(2));
				laddr = InetAddress.getByName(nameArgs.get(4));
			} catch (Exception e) {
				System.err.println("CG " + fullName + " init error: invalid address!");
				return;
			}

			dport = Integer.parseInt(nameArgs.get(3));
			lport = Integer.parseInt(nameArgs.get(5));

		} else {
			System.err.println("CG " + fullName + " init error: invalid command " + nameArgs.get(1));
			return;
		}
	}
	
	// DNSGroup methods =========================================================
	public int getId() {
		return ChordGroup.id;
	}

	public void start() {
		// WE are the server so start it up! (on its own thread)
		thread = new Thread(this);
		thread.start();
	}

	public void run() {
		if (laddr == null) {
				System.err.println("CG " + fullName + " start: no IP address?");
				return;
		}
		
		if (lport == 0) {
			lport = findFreePort();
		}
		
		if (daddr == null || dport == 0) {
			// don't have enough information to join a chord, so create one!
			createChord();
		} else {
			joinChord();
		}
	}

	public void stop() {
		for (Service s : services) {
			serviceRemoved(s);
		}

		System.out.println("CG " + fullName + ": stopped.");
	}

	public boolean createSubGroup(String name) {
		if (chord == null) {
			System.err.println("CG " + fullName + " createSubGroup error: chord == null");
			return false;
		}

		Service s = new Service(name, 1000, mdns);
		s.addr = laddr;
		StringKey key = new StringKey(s.name);		
		Set set;
		
		// make sure there are no other results for this group!
		try {
			set = chord.retrieve(key);
			if (set == null) {
				System.err.println("CG " + fullName + " error: retrieved null set");
				return false;
			}
			if (!set.isEmpty()) {
				return false;
			}
		} catch (Exception e) {
			System.err.println("CG " + fullName + " error: chord.retrieve error!");
			e.printStackTrace();
			return false;
		}
		
		// now try inserting
		try {
			chord.insert(key, s.addr.getHostAddress());
			System.out.println("CG " + fullName + ": inserted " + s.addr + " for key: " + s.name);
			services.add(s);
			return true;
		} catch (Exception e) {
			System.err.println("CG " + fullName + " error: could not insert " + s.addr + " for key: " + s.name);
			e.printStackTrace();
		}
		
		return false;
	}
	
	public void serviceRegistered(Service s) {
		
		if (chord == null) {
			System.err.println("CG " + fullName + " serviceRegistered error: chord == null");
			return;
		}
		
		s.addr = laddr;
		StringKey key = new StringKey(s.name);
		
		try {
			chord.insert(key, s.addr.getHostAddress());
			System.out.println("CG " + fullName + ": inserted " + s.addr + " for key: " + s.name);
			services.add(s);
		} catch (Exception e) {
			System.err.println("CG " + fullName + " error: could not insert " + s.addr + " for key: " + s.name);
			e.printStackTrace();
		}
	}
	
	public void serviceRemoved(Service s) {
		if (chord == null) {
			System.err.println("CG " + fullName + " serviceRemoved error: chord == null");
			return;
		}
		
		StringKey key = new StringKey(s.name);
		services.remove(s);
		
		try {
			chord.remove(key, s.addr);
			System.out.println("CG " + fullName + ": removed " + s.addr + " for key: " + s.name);
		} catch (Exception e) {
			System.err.println("CG " + fullName + " error: could not remove " + s.addr + " for key: " + s.name);
			e.printStackTrace();
		}
	}
	
	public InetAddress resolveService(String name) {
		return resolveService(name, 0);
	}
	
	public InetAddress resolveService(String name, int minScore) {
		if (chord == null) {
			System.err.println("CG " + fullName + " resolveService error: chord == null");
			return null;
		}
		
		System.out.println("CG " + fullName + ": resolving " + name);

		// STEP 1: Produce the key we're looking for!
		String servicename = getServiceName(name);
		
		Set set;
		StringKey key = new StringKey(servicename);
		
		try {
			set = chord.retrieve(key);
		} catch (Exception e) {
			System.err.println("CG " + fullName + " error: chord.retrieve!");
			e.printStackTrace();
			return null;
		}
		
		if (set == null) {
			System.err.println("CG " + fullName + " error: set was null!");
			return null;
		}
		
		if (set.isEmpty()) {
			System.err.println("CG " + fullName + " error: set was empty!");
			return null;
		}
		
		String res = (String) set.iterator().next();
		
		// OKAY: we've gotten a response for a query. If this query
		// was the ACTUAL end-product, return it. Otherwise, forward
		// the request onto THIS query!
		String[] servicegroups = name.split("\\.");
		
		if (servicename.equals(servicegroups[0])) {
			try {
				InetAddress result = InetAddress.getByName(res);
				System.out.println("CG " + fullName + ": returning " + result + "for " + name);
				return result;
			} catch (UnknownHostException e) {
				e.printStackTrace();
				return null;
			}
		} else {
			// indicates we're not prepared to forward?
			// if (minScore == 0) {
			// 	System.err.println("CG " + fullName + " error: minscore return???");
			// 	return null;
			// }
						
			// String vals = res.split(":");
			// if (vals.length < 2) {
			// 	System.out.println("invalid entry!");
			// }
			
			// maybe don't hard-code these in?
			try {
			InetAddress addr = InetAddress.getByName(res);
			int port = 53;
			return mdns.forwardRequest(name, this, addr, port);
			} catch (UnknownHostException e) {
				e.printStackTrace();
				return null;
			}
		}
	}
	
	// cleanup method
	public void exit() {
		if (running) {
			try {
				chord.leave();
				System.out.println("CG " + fullName + ": exited chord");
			} catch (Exception e) {
				System.err.println("CG " + fullName + ": could not exit chord!");
				e.printStackTrace();
			}
		}
	}
	
	// Chord-specific functions! =======================================================
	private void createChord() {
		// de.uniba.wiai.lspi.chord.service.PropertiesLoader.loadPropertyFile();
		String protocol = URL.KNOWN_PROTOCOLS.get(URL.SOCKET_PROTOCOL);
		URL localURL = null;
		
		try {
			localURL = new URL(protocol + "://" + laddr.getHostAddress() + ":" + lport + "/");
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
		
		chord = new de.uniba.wiai.lspi.chord.service.impl.ChordImpl();
		try {
			chord.create(localURL);
		} catch (ServiceException e) {
			throw new RuntimeException("CG " + fullName + " error: could not create", e);
		}
		
		System.out.println("CG " + fullName + ": created chord, serving at " + laddr + ":"+ lport);

		// WE created a chord that's not top-level! register ourself as the 
		// "parent" of this group since, presumably, we are a member of the
		// parent-group and can forward traffic onward appropriately.
		if (groups.length > 1) {
			Service s = new Service("parent", 0, mdns);
			serviceRegistered(s);
		}
	}
	
	private void joinChord() {
		// de.uniba.wiai.lspi.chord.service.PropertiesLoader.loadPropertyFile();
		String protocol = URL.KNOWN_PROTOCOLS.get(URL.SOCKET_PROTOCOL);
		URL localURL = null;
		URL joinURL = null;
		
		if (daddr == null || dport == 0) {
			System.err.println("CG " + fullName + ": cannot join chord, don't know where to find it!");
			return;
		}
				
		try {
			localURL = new URL(protocol + "://" + laddr.getHostAddress() + ":" + lport + "/");
			joinURL = new URL(protocol + "://" + daddr.getHostAddress() + ":" + dport +"/");
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
		
		chord = new de.uniba.wiai.lspi.chord.service.impl.ChordImpl();
		try {
			chord.join(localURL, joinURL);
		} catch (ServiceException e) {
			throw new RuntimeException("CG " + fullName + " error: could not join!", e);
		}
		
		System.out.println("CG " + fullName + ": joined chord at " + daddr + ":" + dport + ", serving at " + laddr + ":" + lport);
	}
	
	public static int findFreePort() {
		ServerSocket server;
		int port;
		try {
			server = new ServerSocket(0);
			port = server.getLocalPort();
		  	server.close();
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	  	return port;
	}

	// public static void main(String[] args) {
	// 	String s;
		
	// 	if (args.length > 0) {
	// 		s = args[0];
	// 	} else {
	// 		s = "join";
	// 	}
		
	// 	Service serv = new Service("creator", 500, null);
		
	// 	if (s.equals("register")) {
	// 		ChordGroup c = new ChordGroup(null, "testchord");
	// 		c.joinChord();
	// 		c.serviceRegistered(serv);
	// 	} else if (s.equals("resolve")) {
	// 		ChordGroup c = new ChordGroup(null, "testchord");
	// 		c.joinChord();
	// 		String res = c.resolveService("creator");
	// 		System.out.println("CHORD RETURNED: " + res);
	// 	} else {
	// 		//create chord
	// 		ChordGroup c = new ChordGroup(null, "testchord");
	// 		c.createChord();
	// 	}
	// }
}
