package multiDNS;

import java.util.*;

public abstract class DNSGroup {
	public String name;
	public String fullName;
	private String[] groups;
	protected MultiDNS mdns;
	public boolean recursive;
	public static int id;
	
	DNSGroup(MultiDNS m, String n) {
		mdns = m;
		recursive = false;
		
		fullName = n;
		groups = n.split("\\.");
		name = groups[0];
		
		// root hierarchy now at the front! (like typical filesystem)
		Collections.reverse(Arrays.asList(groups));
	}
	
	public static final DNSGroup createGroupFromString(MultiDNS m, String arg_string) {
		String[] args = arg_string.split(":");
		DNSGroup group = null;
		
		if (args.length < 1) {
			System.out.println("error: args.length < 1");
			return null;
		}
		
		int type = Integer.parseInt(args[0]);
		if (type == FloodGroup.id) {
			group = new FloodGroup(m, args);
		} else {
			group = null;
		}
		
		return group;
	}
	
	// for interface display
	public String toString() {
		return fullName;
	}
	
	// for BootstrapServer
	protected String getResponse() {
		return null;
	}
	
	protected int calculateScore(String servicename) {
		String[] names = servicename.split("\\.");
		Collections.reverse(Arrays.asList(names));
		int count = 0;
		// the DNSGroup may have a longer name than we specify
		// but this cannot be the other way around!
		// example: "james.ccrg.soe" maps to "ccrg.soe.ucsc" but not to "ccrg.csl.parc"
		// alternatively, "james.ccrg.soe" CAN map to "inrg.soe.ucla" even if unintended
		// THIS means that we have to start searching with the root of the DNS name
		int startIndex = 0;
		
		for(startIndex = 0; startIndex < groups.length; startIndex++) {
			if (names[0].equals(groups[startIndex])) {
				break;
			}
		}
		
		// startIndex now has the index (on fullNames) of the first successful match.
		if (startIndex == groups.length) {
			// there was no match
			return 0;
		}
		
		// make sure we don't go "over the edge"
		int numReps = Math.min(names.length, groups.length - startIndex);
		
		// this for-loop starts at the first successful comparison
		// and then counts the number of successful comparisons thereafter
		for(count = 0; count < numReps; count++, startIndex++) {
			if (!names[count].equals(groups[startIndex])) {
				break;
			}
		}
		
		return count;
	}
	
	// the core methods for all groups
	public abstract void start();
	public abstract void serviceRegistered(Service s);
	public abstract void serviceRemoved(Service s);
	public abstract String resolveService(String name);
	
	public boolean joinGroup() {
		// no work really necessary here?
		start();
		return true;
	}	
	public boolean createSubGroup(String name) {
		return false;
	}
	
	// cleanup method
	public void exit() {
		System.out.println("exiting group: " + name);
	}
}