package com.spencersevilla.mdns;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.prefs.*;
import com.thoughtworks.xstream.XStream;

public class MultiDNS {
    
    protected BootstrapServer bs;
	protected jnamed dns_server;
	protected InterGroupServer igs;
	protected boolean running;
	private String hostname;
	private String address;

	// For User Preferences
	private XStream xstream;
	private static final String service_file = "config/services.xml";
	private static final String group_file = "config/groups.xml";
    
	public ArrayList<DNSGroup> groupList;
	public ArrayList<Service> serviceList;
	public ArrayList<DNSGroup> allGroups;

	// This function only called ONCE! (initializer)
	public MultiDNS() throws Exception {
        running = false;
        hostname = null;
        address = null;

        groupList = new ArrayList<DNSGroup>();
		serviceList = new ArrayList<Service>();
		allGroups = new ArrayList<DNSGroup>();

		xstream = new XStream();
		xstream.alias("service", Service.class);
		xstream.alias("group", DNSGroup.class);
		xstream.alias("list", List.class);

        // load app data
		// loadServices();
		// loadGroups();

		// here we initialize (but don't start) all the servers.
		// We absolutely MUST initialize the jnamed here! this is
		// because it requires a privileged port (UDP53) and we
		// don't want to run MultiDNS start() with root privileges!
        bs = new BootstrapServer(this);
		igs = new InterGroupServer(this);
		dns_server = new jnamed(this);

		System.out.println("MDNS: initialized!");
	}

	// This function can go back-and-forth with start() and stop()
	public void start() throws Exception {
		bs.start();
		igs.start();
		dns_server.start();
		System.out.println("MDNS: started!");
	}
	
	public void stop() throws Exception {
		bs.stop();
		igs.stop();
		dns_server.stop();
		System.out.println("MDNS: stopped!");

		// also pause every service we're a part of
		for (DNSGroup group : groupList) {
			group.stop();
		}
	}

	public void exit() {
		System.out.println("CLEAN UP MDNS HERE!");

		// call "exit" on every service we're a part of
		for (DNSGroup group : groupList) {
			group.stop();
		}
	}

	public void setName(String name) {
		// only allow this to be set ONCE!
		if (hostname != null) {
			return;
		}

		hostname = name;
	}

	protected String getName() {
		return hostname;
	}

	public void setAddr(String addr) {
		// only allow this to be set ONCE!
		if (address != null) {
			return;
		}

		address = addr;
	}

	protected String getAddr() {
		return address;
	}

	private void loadServices() {		
		FileInputStream fis = null;
		ObjectInputStream in = null;
		try {
			fis = new FileInputStream(service_file);
			in = new ObjectInputStream(fis);
			String xml = (String)in.readObject();
			Object[] objs = (Object[]) xstream.fromXML(xml);
			for (Object o : objs) {
				Service s = (Service) o;
				createService(s.name);
			}
			in.close();

		} catch (FileNotFoundException e) {
			// create a very elementary appdata.xml file and save?
			System.err.println("MDNS: no services.xml file!");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// default behavior is to join all the previous groups it can find
	// BUT to not bother creating groups that don't already exist
	private void loadGroups() {
		FileInputStream fis = null;
		ObjectInputStream in = null;
		try {
			fis = new FileInputStream(group_file);
			in = new ObjectInputStream(fis);
			String xml = (String)in.readObject();
			Object[] objs = (Object[]) xstream.fromXML(xml);
			for (Object o : objs) {
				DNSGroup g = (DNSGroup) o;
				// TODO: clean-up this method?
				// createGroup(g.name);
			}
			in.close();

		} catch (FileNotFoundException e) {
			// create a very elementary appdata.xml file and save?
			System.out.println("no groups.xml file!");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void saveServices() {
		Object[] objs = serviceList.toArray();
		String xml = xstream.toXML(objs);
		
		FileOutputStream fos = null;
		ObjectOutputStream out = null;
		try {
			fos = new FileOutputStream(service_file);
			out = new ObjectOutputStream(fos);
			out.writeObject(xml);
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void saveGroups() {
//		Object[] objs = dnsGroups();
		FloodGroup group = new FloodGroup(this, "test2");
		String xml = xstream.toXML(group);
		
		FileOutputStream fos = null;
		ObjectOutputStream out = null;
		try {
			fos = new FileOutputStream(group_file);
			out = new ObjectOutputStream(fos);
			out.writeObject(xml);
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void findOtherGroups() {
		ArrayList<DNSGroup> groups = bs.findGroups();
		
		if (groups == null) {
			return;
		}
		
		// don't add groups we've already seen before (member or otherwise!)
		for (DNSGroup g : groups) {
			if (allGroups.contains(g)) {
				continue;
			}
			allGroups.add(g);
		}
	}
	
	public DNSGroup findGroupByName(String fullName) {
		for (DNSGroup g : groupList) {
			if (g.fullName.equals(fullName)) {
				return g;
			}
		}
		return null;
	}

	public DNSGroup createGroup(int gid, ArrayList<String> args) {
		DNSGroup group = null;
		if (gid == 0) {
			// FloodGroup
			group = new FloodGroup(this, args);
		} else if (gid == 1) {
			// ChordGroup
			group = new ChordGroup(this, args);
		}

		if (group == null) {
			return null;
		}

		allGroups.add(group);
		return group;
	}

	public DNSGroup createSubGroup(DNSGroup parent, int gid, ArrayList<String> args) {
		if (!parent.createSubGroup(args.get(0))) {
			// was not successful!
			System.err.println("MDNS error: could not create subgroup " + args.get(0) + " of group " + parent);
			return null;
		}
		
		// update the name to reflect that it's a subgroup
		String fname = args.get(0) + "." + parent.fullName;
		args.set(0,fname);

		// here we create the according DNSGroup
		return createGroup(gid, args);
	}

	public void leaveGroup(DNSGroup g) {
		g.exit();
		groupList.remove(g);
	}
	
	public void joinGroup(DNSGroup group) {
		if (groupList.contains(group)) {
			// cannot join group we're already a member of
			return;
		}
		
		if (!group.joinGroup()) {
			return;
		}
		
		groupList.add(group);
		
		// tell it about already existing services
		for (Service s : serviceList) {
			group.serviceRegistered(s);
		}
		
		// saveGroups();
	}
	
	public void createService(String name) {
		Service s = new Service(name, 0, this);
		//TODO: CHECK FOR DUPLICATES!
		
		// alert all DNSGroup instances
		for (DNSGroup group : groupList) {
			group.serviceRegistered(s);
		}

		serviceList.add(s);
		
		// saveServices();
	}
	
	public void deleteService(Service s) {
		
		// alert all DNSGroup instances
		for (DNSGroup group : groupList) {
			group.serviceRemoved(s);
		}
		
		serviceList.remove(s);
		
		// saveServices();
	}
	
	public InetAddress resolveService(String servicename) {
		// FIRST: determine scope of address and who to forward it to
		// IF there is no acceptable/reachable group, complain!

		// look out for trailing dot just-in-case (trim if exists)
		if (servicename.endsWith(".")) {
			servicename = servicename.substring(0, servicename.length() - 1);
		}

		// make sure we trapped appropriately from jnamed
		if (!servicename.endsWith(".spencer")) {
			System.err.println("MDNS error: received incorrect servicename " + servicename);
			return null;
		}
		// ok now remove the dns format-key from it
		String trimName = servicename.substring(0, servicename.length() - 8);		
		
		DNSGroup group = findResponsibleGroup(trimName);
		if (group == null) {
			// nowhere to foward, we don't know anyone in this hierarchy!
			// note: MAYBE flooding a request could find cached information
			// BUT that's unlikely and could even be a DDOS attack vector
			return null;
		}
		
		String addr = group.resolveService(trimName);
			if (addr != null) {
				try {
					return InetAddress.getByName(addr);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		return null;
	}
	
	public String forwardRequest(String servicename, int minScore) {
		// forward or rebroadcast the request to a different group if possible
		// minScore ensures that we don't loop: we can only forward to another group
		// if it's a better match than the group this request came from.
		
		DNSGroup g = findResponsibleGroup(servicename, minScore);
		if (g == null) {
			System.err.println("MDNS: cannot forward request " + servicename + ", so ignoring it.");
			return null;
		}
		
		System.out.println("MDNS: forwarding request " + servicename + " internally to group " + g);
		return g.resolveService(servicename, minScore);
	}
	
	public String forwardRequest(String servicename, int minScore, String addr, int port) {
		return igs.resolveService(servicename, minScore, addr, port);
	}
	
	protected DNSGroup findResponsibleGroup(String servicename) {
		return findResponsibleGroup(servicename, 0);
	}
	
	protected DNSGroup findResponsibleGroup(String servicename, int minScore) {

		// cycle through all group memberships
		// give each group a "score" based on longest-prefix
		// then choose the best choice! (null if none accceptable)
		DNSGroup bestChoice = null;
		int highScore = minScore;
		int score;
		for (DNSGroup group : groupList) {
			score = group.calculateScore(servicename);
			if (score > highScore) {
				bestChoice = group;
				highScore = score;
			}
		}
		
		// if (bestChoice != null)
		// 	System.out.println("findResponsibleGroup chose: " + bestChoice + "for string: " + servicename + "with score: " + highScore);
		
		return bestChoice;
	}
		
	public void shutDown() {
		System.exit(0);
	}
}
