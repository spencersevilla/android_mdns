/*  This server is group-agnostic and serves one function: to pass "translate" requests
 *  when they must go to other nodes (such as using the ChordGroup). It is the primary
 *  public interface for a mdns server to request service-resolution from another computer.
 */

package com.spencersevilla.mdns;

import java.io.*;
import java.net.*;
import java.util.*;
import org.xbill.DNS.*;

public class InterGroupServer implements Runnable {

	protected MultiDNS mdns;
	private Thread thread;
	public static int port = 5300;
    protected DatagramSocket socket;
	private boolean listening;
	private volatile boolean running;
	protected ArrayList<InterGroupThread> threads;
	protected ArrayList<Name> dns_names;
	protected ArrayList<InetAddress> dns_addrs;

	InterGroupServer(MultiDNS m) throws Exception {
		mdns = m;
		threads = new ArrayList<InterGroupThread>();
		socket = new DatagramSocket(port);
		dns_names = new ArrayList<Name>();
		dns_addrs = new ArrayList<InetAddress>();
	}

	public void start() {
		running = true;
		System.out.println("IGS: serving on port " + port);

		if (thread == null) {
			thread = new Thread(this);
			thread.start();
		}
	}

	public void stop() throws Exception {
		// stop responding to new requests
		running = false;

		System.out.println("IGS: stopped");

		// abort all running requests
		for (InterGroupThread t : threads) {
			t.exit();
		}
	}

	public void addDNSServer(String sname, InetAddress addr) {
		if (sname == null || addr == null) {
			return;
		}

		try {
			Name name = new Name(sname);
			dns_names.add(name);
			dns_addrs.add(addr);
		} catch (TextParseException e) {
			System.err.println("could not create DNS Name from string " + sname);
			return;
		}

	}

	public void run() {

        byte buf[];
		byte buf2[];
        DatagramPacket pack;
		DatagramPacket pack2;

        try {
			listening = true;

			while(listening) {
				buf = new byte[1024];
				pack = new DatagramPacket(buf, buf.length);
		        socket.receive(pack);

		        if (!running) {
		        	continue;
		        }

				InterGroupThread t = new InterGroupThread(this, pack);
				threads.add(t);
				t.start();
			}
        	socket.close();

 		} catch (Exception e) {
            e.printStackTrace();
        }
	}
	
	public InetAddress resolveService(String servicename, int score, InetAddress addr, int port) {
		try {
			byte[] sendbuf = generateRequest(servicename);
			if (sendbuf == null) {
				System.err.println("IGS error: sendbuf is null");
				return null;
			}
			DatagramPacket sendpack = new DatagramPacket(sendbuf, sendbuf.length, addr, port);

			byte[] recbuf = new byte[1024];
			DatagramPacket recpack = new DatagramPacket(recbuf, recbuf.length);
			DatagramSocket sock = new DatagramSocket();
			sock.setSoTimeout(10000);

			System.out.println("IGS: requesting " + servicename + " from " + addr + ":" + port);

			sock.send(sendpack);

			// Timeout Breaks Here!
			sock.receive(recpack);
			Message m = new Message(recpack.getData());
			InetAddress result = parseResponse(m);

			System.out.println("IGS: " + addr + ":" + port + " returned " + result + " for " + servicename);

			return result;
		} catch (SocketTimeoutException e) {
			// timed out! :-(
			System.err.println("IGS error: socket timed out");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	byte[] generateRequest(String name) {
		try {
			Name n = new Name(name);
			Record query = Record.newRecord(n, Type.A, DClass.IN);
			Message request = Message.newQuery(query);
			return request.toWire();
		} catch (TextParseException e) {
			System.err.println("IGS error: could not generate request");
			return null;
		}
	}

	InetAddress parseResponse(Message response) {
		Header header = response.getHeader();

		if (!header.getFlag(Flags.QR)) {
			System.err.println("IGS error: response not a QR");
			return null;
		}
		if (header.getRcode() != Rcode.NOERROR) {
			System.err.println("IGS error: header Rcode is " + header.getRcode());
			return null;
		}
		Record question = response.getQuestion();

		// sanity check on question here...

		Record[] records = response.getSectionArray(Section.ANSWER);

		// simplest alg: just return the first valid A record...
		Record answer = null;
		for(int i = 0; i < records.length; i++) {
			answer = records[i];
			if (answer.getType() != Type.A) {
				continue;
			}

			byte[] address = answer.rdataToWireCanonical();
			try {
				InetAddress result = InetAddress.getByAddress(address);
				return result;
			} catch (UnknownHostException e) {
				continue;
			}
		}

		return null;
	}
}

class InterGroupThread extends Thread {

	static final int FLAG_DNSSECOK = 1;
	static final int FLAG_SIGONLY = 2;

	DatagramPacket inpacket = null;
    MultiDNS mdns = null;
	ArrayList<InterGroupThread> array = null;
	DatagramSocket socket = null;
	InterGroupServer server = null;
	
    public InterGroupThread(InterGroupServer s, DatagramPacket p) {
    	server = s;
		inpacket = p;
        mdns = server.mdns;
		array = server.threads;
		socket = server.socket;
	}

	public void run() {
		try {
			byte[] response = generateResponse();

			if (response == null) {
				exit();
			}

			DatagramPacket outpacket = new DatagramPacket(response, response.length, 
											inpacket.getAddress(), inpacket.getPort());

			socket.send(outpacket);
		} catch (Exception e) {
			e.printStackTrace();
		}
		exit();
	}

	// this function borrows HEAVILY from jnamed.java's generateReply function!
    byte [] generateResponse() throws IOException {
    	Message query = new Message(inpacket.getData());
    	Header header = query.getHeader();
    	int maxLength = 0;
		int flags = 0;

    	// basic sanity checks
    	if (header.getFlag(Flags.QR))
			return null;
		if (header.getRcode() != Rcode.NOERROR)
			return errorMessage(query, Rcode.FORMERR);
		if (header.getOpcode() != Opcode.QUERY)
			return errorMessage(query, Rcode.NOTIMP);

		// parse out the question from the record
		Record queryRecord = query.getQuestion();
		System.out.println("IGS: received query for " + queryRecord.getName());

		OPTRecord queryOPT = query.getOPT();
		if (socket != null) {
			maxLength = 65535;
		} else if (queryOPT != null) {
			maxLength = Math.max(queryOPT.getPayloadSize(), 512);
		} else {
			maxLength = 512;
		}

		if (queryOPT != null && (queryOPT.getFlags() & ExtendedFlags.DO) != 0)
			flags = FLAG_DNSSECOK;

		// prep the response with DNS data
		Message response = new Message(query.getHeader().getID());
		response.getHeader().setFlag(Flags.QR);
		if (query.getHeader().getFlag(Flags.RD)) {
			response.getHeader().setFlag(Flags.RD);
		}
		response.addRecord(queryRecord, Section.QUESTION);

    	// OKAY! now we've got a DNS Record question here. It should contain a 
    	// name, type, class, ttl, and rdata. Now we should parse the request
    	// and then execute one of four potential paths:
    	// 		1) forward the request to the next server along the way 
    	//		2) return next server's addr (non-recursive #1)
    	//		3) respond in the positive (found the node)
    	//		4) respond in the negative (end-of-the-line)

		byte rcode = generateAnswer(queryRecord, response, flags);

		if (rcode != Rcode.NOERROR && rcode != Rcode.NXDOMAIN) {
			return errorMessage(query, rcode);
		}

		// addAdditional?

		// response.setTSIG(null, Rcode.NOERROR, null);
		return response.toWire(maxLength);
	}

	byte generateAnswer(Record query, Message response, int flags) {
		Name name = query.getName();
		int type = query.getType();
		int dclass = query.getDClass();
		byte rcode = Rcode.NOERROR;
		int ttl = 0;

		if (dclass != DClass.IN) {
			return Rcode.NOTIMP;
		}

    	String nameString = name.toString();

		// ok now remove the dns format-key from it
		if (nameString.endsWith(".")) {
			nameString = nameString.substring(0, nameString.length() - 1);
		}

		if (nameString.endsWith(".dssd")) {
			nameString = nameString.substring(0, nameString.length() - 5);
		} else {
			System.out.println("IGS: received request for alternative TLD, returning DNS referrals");
			// this is a TLD that we don't support. Perhaps another DNS server will?
			return generateReferral(query, response, flags);
		}

		if (type == Type.A) {
			// They are asking to resolve a stringname to an address...
			// So, standard-operating-procedure basically!
			InetAddress addr = mdns.resolveService(nameString);

			System.out.println("IGS: returning " + addr + " for " + name);

			if (addr == null) {
				return Rcode.NXDOMAIN;
			}

			byte[] rdata = addr.getAddress();

			Record rec = Record.newRecord(name, type, dclass, ttl, rdata);
			RRset r = new RRset(rec);

			addRRset(name, response, r, Section.ANSWER, flags);
			return Rcode.NOERROR;
		} else if (type == Type.CNAME) {
			// domain name aliases here? Not exactly sure how we
			// want to handle this situation, so ignore for now!
		} else if (type == Type.NS) {
			// Here the request entails a nameserver-lookup
			// Which means that this is really an intermediate-request
			// and we want to return the pointer to the next server?
		}
		return Rcode.REFUSED;
	}

	byte generateReferral(Record query, Message response, int flags) {
		// This function returns a list of all other DNS servers we're aware of.
		// The presumption is that this TLD is not our domain, so we will
		// hook into the "traditional" DNS hierarchy to see if they might
		// have an answer. If it's not supported by them, then let THEM say so.
		// Note that we're returning the list of DNS servers and then we're done with
		// it... this is an example of non-recursive response.
		Name name = query.getName();
		int ttl = 0;

		for (int i = 0; i < server.dns_names.size(); i++) {
			byte[] rdata_auth = server.dns_names.get(i).toWireCanonical();
			byte[] rdata_addl = server.dns_addrs.get(i).getAddress();
			Record auth = Record.newRecord(name, Type.NS, DClass.IN, ttl, rdata_auth);
			Record addl = Record.newRecord(server.dns_names.get(i), Type.A, DClass.IN, ttl, rdata_addl);

			response.addRecord(auth, Section.AUTHORITY);
			response.addRecord(addl, Section.ADDITIONAL);
		}

		return Rcode.NOERROR;
	}

	byte [] errorMessage(Message query, int rcode) {	
		return buildErrorMessage(query.getHeader(), rcode,
					 query.getQuestion());
	}

	byte [] buildErrorMessage(Header header, int rcode, Record question) {
		Message response = new Message();
		response.setHeader(header);
		for (int i = 0; i < 4; i++)
			response.removeAllRecords(i);
		if (rcode == Rcode.SERVFAIL)
			response.addRecord(question, Section.QUESTION);
		header.setRcode(rcode);
		return response.toWire();
	}

	void addRRset(Name name, Message response, RRset rrset, int section, int flags) {
		for (int s = 1; s <= section; s++)
			if (response.findRRset(name, rrset.getType(), s))
				return;
		if ((flags & FLAG_SIGONLY) == 0) {
			Iterator it = rrset.rrs();
			while (it.hasNext()) {
				Record r = (Record) it.next();
				if (r.getName().isWild() && !name.isWild())
					r = r.withName(name);
				response.addRecord(r, section);
			}
		}
		if ((flags & (FLAG_SIGONLY | FLAG_DNSSECOK)) != 0) {
			Iterator it = rrset.sigs();
			while (it.hasNext()) {
				Record r = (Record) it.next();
				if (r.getName().isWild() && !name.isWild())
					r = r.withName(name);
				response.addRecord(r, section);
			}
		}
	}

	public void exit() {
		array.remove(this);
	}
}