package com.spencersevilla.mdns;

import java.util.*;
import java.net.InetAddress;


public class CacheEntry {
	public String fullName;
	public InetAddress addr;
	public int port;
	public boolean server;

	public CacheEntry(String n, InetAddress a) {
		fullName = n;
		addr = a;
		port = 0;
		server = false;
	}


	// this function is stolen shamelessly from DNSGroup. Here, "groups"
	// is generated from the CacheEntry's fullName by splitting/reversing
	// the string, ie "john.csl.global" -> ["global", "csl", "john"]

	public int calculateScore(String servicename) {
		String[] groups = fullName.split("\\.");
		Collections.reverse(Arrays.asList(groups));

		String[] names = servicename.split("\\.");
		Collections.reverse(Arrays.asList(names));
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

	public boolean isExactMatch(String servicename) {
		String[] request = fullName.split("\\.");
		String[] entry = servicename.split("\\.");

		// the entry's fullname may be longer than the request
		// but the request can never be longer than the fullname!
		if (request.length > entry.length) {
			return false;
		}

		for (int i = 0; i < request.length; i++) {
			if (!request[i].equals(entry[i])) {
				return false;
			}
		}

		return true;
	}
}
