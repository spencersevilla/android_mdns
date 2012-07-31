package multiDNS;

import javax.swing.JFrame;
import javax.swing.JLabel;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.prefs.*;
import com.thoughtworks.xstream.XStream;

public class MultiDNS {
    
    protected BootstrapServer is;
    protected SwingGui gui;
	protected DNSGroup floodGroup;
	protected jnamed dns_server;

	// For User Preferences
	private XStream xstream;
	private static final String service_file = "config/services.xml";
	private static final String group_file = "config/groups.xml";
	
	public static void main(String args[]) {
		new MultiDNS(args);
	}
    
	MultiDNS(String args[]) {
        
        System.out.println("Starting Up...");
        
		xstream = new XStream();
		xstream.alias("service", Service.class);
		xstream.alias("group", DNSGroup.class);
		xstream.alias("list", List.class);
				
		// launch gui
		gui = new SwingGui(this);

		// command-line option to join/create/ignore the chord group
		String command = "node";
		if (args.length > 0) {
			command = args[0];
		}
		
		System.out.println("operating as a node, NOT joining any chords...");
		
        // load app data
		// loadServices();
		// loadGroups();

		// start the basic GRP_REQ/REP server
        is = new BootstrapServer(this);
		
        // start a logger?

		// finally, listen for API calls
		try {
			dns_server = new jnamed(this);
		}
		catch (Exception e) {
			System.out.println(e);
		}
	}
	
	public DNSGroup[] dnsGroups() {
		DNSGroup[] dg_array = Arrays.copyOf(gui.listModel.toArray(), gui.listModel.toArray().length, DNSGroup[].class);
		return dg_array;
	}

	public Service[] services() {
		Service[] service_array = Arrays.copyOf(gui.serviceListModel.toArray(), gui.serviceListModel.toArray().length, Service[].class);
		return service_array;
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
				createGroup(g.name);
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
		Object[] objs = gui.serviceListModel.toArray();
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
	
	public DNSGroup[] findOtherGroups() {
		DNSGroup[] groups = is.findGroups();
		return groups;
	}
	
	public void createGroup(String gname) {
		// here we already assume the group is not around

		FloodGroup group = new FloodGroup(this, gname);
		group.start();
		gui.listModel.addElement(group);
			
		// tell it about already existing services
		for (Service s : services()) {
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
		createGroup(fname);
	}
	
	public void createAdHocGroup(String name) {
		String fname = name + ".adhoc";
		createGroup(fname);
	}
	
	public void deleteGroup(DNSGroup g) {
		g.exit();
		
		gui.listModel.removeElement(g);
		
		// saveGroups();
	}
	
	public void joinGroup(DNSGroup group) {
		if (!group.joinGroup()) {
			return;
		}
		
		gui.otherGroupsListModel.removeElement(group);
		gui.listModel.addElement(group);
		
		// tell it about already existing services
		for (Service s : services()) {
			group.serviceRegistered(s);
		}
		
		// saveGroups();
	}
	
	public void createService(String name) {
		Service s = new Service(name, 0);
		//TODO: CHECK FOR DUPLICATES!
		
		// alert all DNSGroup instances
		for (DNSGroup group : dnsGroups()) {
			group.serviceRegistered(s);
		}
		
		gui.serviceListModel.addElement(s);
		
		saveServices();
	}
	
	public void deleteService(Service s) {
		
		// alert all DNSGroup instances
		for (DNSGroup group : dnsGroups()) {
			group.serviceRemoved(s);
		}
		
		gui.serviceListModel.removeElement(s);
		
		saveServices();
	}
	
	public InetAddress resolveService(String servicename) {
		// FIRST: determine scope of address and who to forward it to
		// IF there is no acceptable/reachable group, complain!

		if (!servicename.endsWith("spencer.")) {
			System.out.println("multiDNS: received incorrect servicename: " + servicename);
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
		
		String addr = group.resolveService(servicename);
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
		for (DNSGroup group : dnsGroups()) {
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
