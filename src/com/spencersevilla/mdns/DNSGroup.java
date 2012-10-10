package com.spencersevilla.mdns;

import java.util.*;
import java.net.InetAddress;

public abstract class DNSGroup {
	public String name;
	public String fullName;
	protected String[] groups;
	protected MultiDNS mdns;
	public boolean recursive;
	public DNSGroup parent;
	
// These functions are SPECIFIC to DNSGroup and may NOT be overridden by another class
// ======================================================================================

	public DNSGroup(MultiDNS m, String n) {
		mdns = m;
		recursive = false;
		parent = null;
		fullName = n;
		groups = n.split("\\.");
		name = groups[0];
		
		// root hierarchy now at the front! (like typical filesystem)
		Collections.reverse(Arrays.asList(groups));
	}
	
	// This function identifies every DNSGroup uniquely using its fullname!
	// We might want to cover some other strcmps for formatting/subgroups/etc
	@Override public final boolean equals(Object otherObject) {
		// check for self-comparison
		if ( this == otherObject ) return true;
		// check for null and ensure class membership
		if ( !(otherObject instanceof DNSGroup) ) return false;
		
		DNSGroup that = (DNSGroup) otherObject;
		
		// traps for null-cases 
		return this.fullName == null ? that.fullName == null : this.fullName.equals(that.fullName);
	}
	
	// useful hook/massager into createGroupFromArgs
	public static final DNSGroup createGroupFromString(MultiDNS m, String arg_string) {
		String[] args = arg_string.split(":");
		DNSGroup group = null;
		
		if (args.length < 1) {
			System.out.println("error: args.length < 1");
			return null;
		}

		ArrayList<String> arglist = new ArrayList<String>(Arrays.asList(args));
		int type = Integer.parseInt(arglist.remove(0));
		
		return createGroupFromArgs(m, type, arglist);
	}
	
	// This is the ONE entry-point for parsing a group ID and creating 
	// the appropriate DNSGroup. In theory, this is the ONLY spot 
	// that should have to be changed to support an additional group type.
	public static final DNSGroup createGroupFromArgs(MultiDNS m, int gid, ArrayList<String> args) {
		DNSGroup group = null;

		if (gid == FloodGroup.id) {
			group = new FloodGroup(m, args);
		} else if (gid == ChordGroup.id) {
			group = new ChordGroup(m, args);
		} else if (gid == ServerGroup.id) {
			group = new ServerGroup(m, args);
		} else {
			group = null;
		}
		
		return group;
	}

	protected final int calculateScore(String servicename) {
		return DNSGroup.calculateScore(servicename, fullName);
	}

	protected static final int calculateScore(String servicename, String groupname) {
		String[] names = servicename.split("\\.");
		Collections.reverse(Arrays.asList(names));

		String[] groups = groupname.split("\\.");
		Collections.reverse(Arrays.asList(groups));

		int count = 0;
		// the DNSGroup may have a longer name than we specify
		// but this cannot be the other way around!
		// example: "james.ccrg.soe" maps to "ccrg.soe.ucsc" but not to "ccrg.csl.parc"
		// alternatively, "james.ccrg.soe" CAN map to "inrg.soe.ucla" even if unintended
		// THIS means that we have to start searching with the root of the DNS name requested
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
		for(count = 0; count < numReps; count++) {
			if (!names[count].equals(groups[startIndex + count])) {
				break;
			}
		}
		
		// last addition: if we maxed-out the group's full name, add 1 to signify that
		// we want to choose it as a parent! ie: searching for "james.csl.parc.global" gives:
		// "isl.parc.global" -> 2
		// "parc.global" -> 3 because it really represents "*.parc.global"
		// "csl.parc.global" -> 4 to distinguish from above, employs same logic
		if (count == (groups.length - startIndex)) {
			count++;
		}
		
		// System.out.println("groups.length = " + groups.length + " startIndex = " + startIndex);
		
		return count;
	}
	
	// this returns a boolean that shows if this group is as good 
	// of a match as could possibly be made! "true" here means we will never
	// forward - either we find the service at this group or it doesn't exist
	// note that we subtract one from score for the servicename itself
	protected final boolean isBestPossibleMatch(String name) {
		String[] names = name.split("\\.");
		int maxLength = names.length;
		int retVal = calculateScore(name);
		return (retVal == maxLength);
	}
	
	protected final String getServiceName(String fullname) {
		// GOAL: compare the fullname "spencer.csl.parc"
		// with the chord name "parc.global" to produce the string
		// "csl" which will be the key that the chord will search for!

		String[] querygroups = fullname.split("\\.");
		Collections.reverse(Arrays.asList(querygroups));
		
		// NOW:
		// querygroups = [parc, csl, spencer] and groups = [global, parc].
		// First, let's extend the querygroups to be absolute! This is also
		// a sanity check, we'll bail-out if we can't find a match here...
		querygroups = absoluteQuery(querygroups, groups);
		if (querygroups == null) {
			return null;
		}

		// Now, we go down the list until we find the first non-match in
		// the query. If it's a member of this group, we return its name, 
		// if it's above this group we return "parent".
		for (int i = 0; i < groups.length; i++) {
			if (!querygroups[i].equals(groups[i])) {
				return new String("parent");
			}
		}

		return querygroups[groups.length];
	}

	private String[] absoluteQuery(String[] query, String[] groupname) {
		// GOAL: given the query [parc, csl, spencer] to group [global, parc]
		// we wish to produce the full query [global, parc, csl, spencer].
		// if a match is not possible (ie query = [att, csl, spencer]) then
		// this group could not resolve the name, so it should return NULL.


		// we know the groupname is full/final, so it MUST contain the root
		// of the query somewhere. If not, then we can't possibly resolve it!
		String firstGroup = query[0];
		int startIndex;
		for (startIndex = 0; startIndex < groupname.length; startIndex++) {
			if (groupname[startIndex].equals(firstGroup)) {
				break;
			}
		}
		if (startIndex == groupname.length) {
			// could not find a match, so bail out!
			return null;
		}

		String[] ret = new String[query.length + startIndex];
		// first, fill-in the values from the groupname
		for (int i = 0; i < startIndex; i++) {
			ret[i] = groupname[i];
		}

		//next, complete the array with values from the query
		for (int i = 0; i < query.length; i++) {
			ret[i + startIndex] = query[i];
		}

		return ret;
	}

	// for interface display
	public final String toString() {
		return fullName;
	}
	

// These functions MAY be overridden but are provided for ease-of-use in development
// ======================================================================================

	// for BootstrapServer
	protected String getResponse() {
		return null;
	}
	
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

// These functions MUST be overridden: they provide the core DNSGroup functionality!
// ======================================================================================

	public abstract int getId();
	public abstract void start();
	public abstract void stop();
	public abstract void serviceRegistered(Service s);
	public abstract void serviceRemoved(Service s);
	public abstract InetAddress resolveService(String name);
	public abstract InetAddress resolveService(String name, int minScore);
}