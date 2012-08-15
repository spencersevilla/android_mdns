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
public class ChordGroup extends DNSGroup {
	private Chord chord;
	private boolean running;
	public static final int id = 1;
	public String laddr = null;
	public int lport = 0;
	public String daddr = null;
	public int dport = 0;

	ChordGroup(MultiDNS m, String n) {
		super(m, n);
	}
	
	ChordGroup(MultiDNS m, ArrayList<String> nameArgs) {
		super(m, nameArgs.get(0));

		if (nameArgs.get(1).equals("create")) {
			if (nameArgs.size() > 2) {
				lport = Integer.parseInt(nameArgs.get(2));
			}
		} else if (nameArgs.get(1).equals("join")) {
			if (nameArgs.size() < 4) {
				System.err.println("CG " + fullName + " init error: invalid init string!");
			}
			daddr = nameArgs.get(2);
			dport = Integer.parseInt(nameArgs.get(3));

			if (nameArgs.size() > 4) {
				lport = Integer.parseInt(nameArgs.get(4));
			}
		} else {
			System.err.println("CG " + fullName + " init error: invalid command " + nameArgs.get(1));
		}
	}
	
	// DNSGroup methods =========================================================
	public void start() {
		laddr = mdns.getAddr();
		if (laddr == null) {
			try {
				laddr = InetAddress.getLocalHost().getHostName();
			} catch (Exception e) {
				System.err.println("CG " + fullName + " start: getLocalHost failed?");
				return;
			}
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
		System.out.println("CG " + fullName + " stopping.");
	}

	public boolean createSubGroup(String name) {
		if (chord == null) {
			System.err.println("CG " + fullName + " createSubGroup error: chord == null");
			return false;
		}

		Service s = new Service(name, 1000, mdns);
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
			chord.insert(key, s.addr);
			System.out.println("CG " + fullName + ": inserted " + s.addr + " for key: " + s.name);
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
		
		StringKey key = new StringKey(s.name);
		
		try {
			chord.insert(key, s.addr);
			System.out.println("CG " + fullName + ": inserted " + s.addr + " for key: " + s.name);
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
		
		try {
			chord.remove(key, s.addr);
			System.out.println("CG " + fullName + ": removed " + s.addr + " for key: " + s.name);
		} catch (Exception e) {
			System.err.println("CG " + fullName + " error: could not remove " + s.addr + " for key: " + s.name);
			e.printStackTrace();
		}
	}
	
	public String resolveService(String name) {
		return resolveService(name, 0);
	}
	
	public String resolveService(String name, int minScore) {
		if (chord == null) {
			System.err.println("CG " + fullName + " resolveService error: chord == null");
			return null;
		}
		
		// STEP 1: PRODUCE A SERVICENAME!
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
			return null;
		}
		
		if (set.isEmpty()) {
			return null;
		}
		
		String res = (String) set.iterator().next();
		
		// OKAY: we've gotten a response for a query. If this query
		// was the ACTUAL end-product, return it. Otherwise, forward
		// the request onto THIS query!
		String[] servicegroups = name.split("\\.");
		
		if (servicename.equals(servicegroups[0])) {
			return res;
		} else {
			// indicates we're not prepared to forward?
			if (minScore == 0) {
				return null;
			}
						
			// String vals = res.split(":");
			// if (vals.length < 2) {
			// 	System.out.println("invalid entry!");
			// }
			
			// maybe don't hard-code these in?
			String addr = res;
			int port = 5300;
			
			return mdns.forwardRequest(name, minScore, addr, port);
		}
	}
	
	private String getServiceName(String fullname) {
		// GOAL: compare the fullname "spencer.csl.parc"
		// with the chord name "parc.global" to produce the string
		// "csl" which will be the key that the chord will search for!
		String[] servicegroups = fullname.split("\\.");
		Collections.reverse(Arrays.asList(servicegroups));
		
		// NOW: we're comparing the array servicegroups [parc, csl, spencer] with
		// the chordgroup fullname array groups [global, parc] to figure out which
		// entry in servicegroups we're looking for!
		int startIndex = 0;
		
		for(startIndex = 0; startIndex < groups.length; startIndex++) {
			if (servicegroups[0].equals(groups[startIndex])) {
				break;
			}
		}
		
		if (startIndex == groups.length) {
			System.err.println("CG " + fullName + ": getServiceName didn't find the name?");
			return null;
		}
		
		// HERE: startIndex has the first "hit" in the chordgroup fullname array
		// The only way this function can operate is by going THROUGH the entire
		// chordgroup's full name and finding it's first child. If this doesn't work,
		// then there's an error! ie a ChordGroup of "parc.usa.global" can only answer
		// queries for one level down, ie "XXX.parc.usa.global".
		int retIndex = groups.length - startIndex;
		if (retIndex < 0 || servicegroups.length <= retIndex) {
			System.err.println("CG " + fullName + ": getServiceName bounds error");
			return null;
		}
		
		// double-check but every entry here should be equal!
		for (int i = 0; i < retIndex; i++) {
			if (!servicegroups[i].equals(groups[startIndex + i])) {
				System.err.println("CG " + fullName + ": getServiceName could not make sense of groups");
				return null;
			}
		}
		
		return servicegroups[retIndex];
		// Chord CANNOT answer anything at all for "*.att.usa.global", "*.usa.global", etc.
		// Find a way to forward up? Maybe a key for "parent"?
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
		de.uniba.wiai.lspi.chord.service.PropertiesLoader.loadPropertyFile();
		String protocol = URL.KNOWN_PROTOCOLS.get(URL.SOCKET_PROTOCOL);
		URL localURL = null;
		
		try {
			localURL = new URL(protocol + "://" + laddr + ":" + lport + "/");
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
	}
	
	private void joinChord() {
		de.uniba.wiai.lspi.chord.service.PropertiesLoader.loadPropertyFile();
		String protocol = URL.KNOWN_PROTOCOLS.get(URL.SOCKET_PROTOCOL);
		URL localURL = null;
		URL joinURL = null;
		
		if (daddr == null || dport == 0) {
			System.err.println("CG " + fullName + ": cannot join chord, don't know where to find it!");
			return;
		}
				
		try {
			localURL = new URL(protocol + "://" + laddr + ":" + lport + "/");
			joinURL = new URL(protocol + "://" + daddr + ":" + dport +"/");
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
		
		chord = new de.uniba.wiai.lspi.chord.service.impl.ChordImpl();
		try {
			chord.join(localURL, joinURL);
		} catch (ServiceException e) {
			throw new RuntimeException("CG " + fullName + " error: could not join!", e);
		}
		
		System.out.println("CG " + fullName + ": joined chord at " + daddr + ":" + dport + ", serving on local port " + lport);
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

	public static void main(String[] args) {
		String s;
		
		if (args.length > 0) {
			s = args[0];
		} else {
			s = "join";
		}
		
		Service serv = new Service("creator", 500, null);
		
		if (s.equals("register")) {
			ChordGroup c = new ChordGroup(null, "testchord");
			c.joinChord();
			c.serviceRegistered(serv);
		} else if (s.equals("resolve")) {
			ChordGroup c = new ChordGroup(null, "testchord");
			c.joinChord();
			String res = c.resolveService("creator");
			System.out.println("CHORD RETURNED: " + res);
		} else {
			//create chord
			ChordGroup c = new ChordGroup(null, "testchord");
			c.createChord();
		}
	}
}
