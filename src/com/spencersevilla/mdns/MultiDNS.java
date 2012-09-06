package com.spencersevilla.mdns;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.prefs.*;
import com.thoughtworks.xstream.XStream;

public class MultiDNS {
    
    protected BootstrapServer bs;
	protected InterGroupServer igs;
	protected boolean running;
	private String hostname;
	private InetAddress address;

	// For User Preferences
	private XStream xstream;
	private static final String service_file = "config/services.xml";
	private static final String group_file = "config/groups.xml";
    
	public ArrayList<DNSGroup> groupList;
	public ArrayList<CacheEntry> cacheList;
	public ArrayList<Service> serviceList;
	public ArrayList<DNSGroup> allGroups;

	// This function only called ONCE! (initializer)
	public MultiDNS() throws Exception {
        running = false;
        hostname = null;
        address = null;

        groupList = new ArrayList<DNSGroup>();
        cacheList = new ArrayList<CacheEntry>();
		serviceList = new ArrayList<Service>();
		allGroups = new ArrayList<DNSGroup>();

		xstream = new XStream();
		xstream.alias("service", Service.class);
		xstream.alias("group", DNSGroup.class);
		xstream.alias("list", List.class);

        // load app data
		// loadServices();
		// loadGroups();

		// here we initialize (but don't start) all of the servers.
        bs = new BootstrapServer(this);
		igs = new InterGroupServer(this);

		System.out.println("MDNS: initialized!");
	}

	// This function can go back-and-forth with start() and stop()
	public void start() throws Exception {
		bs.start();
		igs.start();
		System.out.println("MDNS: started!");
	}
	
	public void stop() throws Exception {
		bs.stop();
		igs.stop();
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

		try {
			InetAddress a = InetAddress.getByName(addr);
			address = a;
		} catch (UnknownHostException e) {
			System.err.println("MDNS error: given an invalid address!");
			e.printStackTrace();
		}
	}

	protected InetAddress getAddr() {
		return address;
	}

	public void addDNSServer(String name, String address) {
		try {
			InetAddress addr = InetAddress.getByName(address);
			igs.addDNSServer(name, addr);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
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
		DNSGroup group = DNSGroup.createGroupFromArgs(this, gid, args);

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
		DNSGroup g = createGroup(gid, args);

		if (g != null) {
			g.parent = parent;
		}

		return g;
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
		InetAddress addr = null;

		// look out for trailing dot just-in-case (trim if exists)
		if (servicename.endsWith(".")) {
			servicename = servicename.substring(0, servicename.length() - 1);
		}

		// "group" is the best-match-DNS-group available to us!
		DNSGroup group = findResponsibleGroup(servicename);

		// FIRST: is this me?
		addr = checkSelf(servicename, group);
		if (addr != null) {
			return addr;
		}

		// NEXT: do we have any cached group information?
		addr = askCache(servicename, group);
		if (addr != null) {
			return addr;
		}

		// LAST: go ahead and query the group, if it exists
		if (group == null) {
			// nowhere to foward, we don't know anyone in this hierarchy!
			// note: MAYBE flooding a request could find cached information
			// BUT that's unlikely and could even be a DDOS attack vector
			System.err.println("MDNS error: could not find a responsible group for " + servicename);
			return null;
		}
		
		addr = group.resolveService(servicename);
		return addr;
	}
	
	private InetAddress checkSelf(String servicename, DNSGroup group) {
		// This function looks to see if a combination of service offered and
		// groups joined can create the entire stringname (making this node 
		// the responsible one.) If so, we can avoid resolution completely!

		// FIRST: we don't even have a group hit here, so we can't possibly be a match!
		if (group == null) {
			return null;
		}

		String[] hierarchy = servicename.split("\\.");
		int namelen = hierarchy.length;

		int groupscore = group.calculateScore(servicename);

		// example: we are resolving john.csl.parc.global

		if (groupscore == namelen + 1) {
			// servicename was actually a groupname and we're a member!
			if (this.address != null) {
				return this.address;
			} else {
				return Service.generateAddress();
			}
		}

		if (groupscore == namelen) {
			// we are a member of "csl.parc.global"
			// so search for "john" in our services
			for (Service s : serviceList) {
				if (s.name.equals(hierarchy[0])) {
					return s.addr;
				}
			}
		}

		// can't possibly be true at this point!
		return null;
	}

	public InetAddress forwardRequest(String servicename, DNSGroup group) {
		// forward or rebroadcast the request to a different group if possible
		// minScore ensures that we don't loop: we can only forward to another group
		// if it's a better match than the group this request came from.
		
		DNSGroup g = findResponsibleGroup(servicename, group);

		if (g == null) {
			System.err.println("MDNS: cannot forward request " + servicename + ", so ignoring it.");
			return null;
		}
		
		System.out.println("MDNS: forwarding request " + servicename + " internally to group " + g);
		return g.resolveService(servicename);
	}
	
	public InetAddress forwardRequest(String servicename, DNSGroup group, InetAddress addr, int port) {
		System.out.println("MDNS: forwarding request " + servicename + " to " + addr + ":" + port);
		return igs.resolveService(servicename, 0, addr, port);
	}
	
	private InetAddress askCache(String servicename, DNSGroup group) {
		// Step 1: Find closest-match entry. Note that this function is
		// executed BEFORE we ask the group to resolve, so we should only
		// return an answer here if we can do better than just the group itself.
		int groupScore = 0;

		if (group != null) {
			groupScore = group.calculateScore(servicename);
		}

		CacheEntry bestChoice = null;
		int highScore = groupScore;
		int score;

		for (CacheEntry entry : cacheList) {
			score = entry.calculateScore(servicename);
			if (score > highScore) {
				bestChoice = entry;
				highScore = score;
			}
		}

		// Step 2: if EXACT match, return address. If not, if it's a
		// DNS server then go ahead and forward the message! If it's
		// not an exact match nor a server, go ahead and bail out(???)
		if (bestChoice == null) {
			return null;
		}

		// this is the exact-match, so return it!
		if (bestChoice.isExactMatch(servicename)) {
			return bestChoice.addr;
		}

		// this is a better DNS server to request from, so forward it on!
		if (bestChoice.server == true) {
			return forwardRequest(servicename, null, bestChoice.addr, bestChoice.port);
		}

		// not the best match nor a DNS server, so we can't do anything with it.
		return null;
	}

	protected DNSGroup findResponsibleGroup(String servicename) {
		return findResponsibleGroup(servicename, null);
	}
	
	protected DNSGroup findResponsibleGroup(String servicename, DNSGroup initial) {

		// cycle through all group memberships
		// give each group a "score" based on longest-prefix
		// then choose the best choice! (null if none accceptable)
		DNSGroup bestChoice = null;
		int highScore = 0;

		if (initial != null) {
			bestChoice = initial;
			highScore = initial.calculateScore(servicename);
		}

		int score;
		for (DNSGroup group : groupList) {
			score = group.calculateScore(servicename);
			if (score > highScore || (bestChoice != null && score == highScore && group == bestChoice.parent)) {
				bestChoice = group;
				highScore = score;
			}
		}
		
		// avoid looping! DO NOT rebroadcast a request on the group it came in on...
		if (bestChoice == initial) {
			bestChoice = null;
		}

		return bestChoice;
	}
		
	public void shutDown() {
		System.exit(0);
	}
}
