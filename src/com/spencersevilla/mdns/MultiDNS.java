package com.spencersevilla.mdns;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.prefs.*;
import com.thoughtworks.xstream.XStream;

public class MultiDNS {
    
    protected BootstrapServer is;
	protected jnamed dns_server;

	// For User Preferences
	private XStream xstream;
	private static final String service_file = "config/services.xml";
	private static final String group_file = "config/groups.xml";
    
	protected ArrayList<DNSGroup> groupList;
	protected ArrayList<Service> serviceList;
	protected ArrayList<DNSGroup> allGroups;

	MultiDNS() {
        
        System.out.println("Starting Up...");
        
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

		// start the basic GRP_REQ/REP server
        is = new BootstrapServer(this);
		
        // start a logger?

		//finally, listen for API calls
		try {
			dns_server = new jnamed(this);
		}
		catch (Exception e) {
			System.out.println(e);
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
			System.out.println("no services.xml file!");
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
		ArrayList<DNSGroup> groups = is.findGroups();
		
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
	
	private void createGroup(String gname, int id) {
		// here we already assume the group is not around
		DNSGroup group = null;
		
		if (id == 0) {
			group = new FloodGroup(this, gname);
		}
		
		if (group == null) {
			return;
		}
		
		group.start();
		groupList.add(group);
		allGroups.add(group);
		
		// tell it about already existing services
		for (Service s : serviceList) {
			group.serviceRegistered(s);
		}
		
		// saveGroups();
	}
	
	public void createSubGroup(DNSGroup parent, String name) {
		if (!parent.createSubGroup(name)) {
			// was not successful!
			System.out.println("error: could not create subgroup");
			return;
		}
		
		String fname = name + "." + parent.fullName;
		
		// here we create the according DNSGroup
		createGroup(fname, 0);
	}
	
	public void createAdHocGroup(String name) {
		String fname = name + ".adhoc";
		createGroup(fname, 0);
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
		Service s = new Service(name, 0);
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

		// trim trailing dot just-in-case
		if (servicename.endsWith(".")) {
			servicename = servicename.substring(0, servicename.length() - 1);
		}

		// make sure we trapped appropriately from jnamed
		if (!servicename.endsWith("spencer")) {
			System.out.println("multiDNS: received incorrect servicename: " + servicename);
			return null;
		}
		// ok now remove the dns format-key from it
		String trimName = servicename.substring(0, servicename.length() - 7);
		
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
	
	private DNSGroup findResponsibleGroup(String servicename) {

		// cycle through all group memberships
		// give each group a "score" based on longest-prefix
		// then choose the best choice! (null if none accceptable)
		DNSGroup bestChoice = null;
		int highScore = 0;
		int score;
		for (DNSGroup group : groupList) {
			score = group.calculateScore(servicename);
			if (score > highScore) {
				bestChoice = group;
				highScore = score;
			}
		}
		
		System.out.println("findResponsibleGroup chose: " + bestChoice + "for string: " + servicename + "with score: " + highScore);
		return bestChoice;
	}
		
	public void shutDown() {
		System.exit(0);
	}
}
