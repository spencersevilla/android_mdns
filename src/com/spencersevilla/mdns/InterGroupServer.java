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
	protected ArrayList<InetAddress> dns_addrs;

	InterGroupServer(MultiDNS m) throws Exception {
		mdns = m;
		threads = new ArrayList<InterGroupThread>();
		socket = new DatagramSocket(port);
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
		for (Iterator<InterGroupThread> it = threads.iterator(); it.hasNext(); ) {
			InterGroupThread t = it.next();
			it.remove();
		}
	}

	public void addDNSServer(InetAddress addr) {
		if (addr == null) {
			return;
		}

		dns_addrs.add(addr);
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

			long start_time = System.currentTimeMillis();
			sock.send(sendpack);

			// Timeout Breaks Here!
			sock.receive(recpack);
			long elapsed_millis = System.currentTimeMillis() - start_time;

			Message m = new Message(recpack.getData());
			InetAddress result = parseResponse(m);

			System.out.println("IGS: " + addr + ":" + port + " returned " + result + " for " + servicename + " in " + elapsed_millis + " milliseconds.");

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
			if (!name.endsWith(".")) {
				name = name.concat(".");
			}

			if (!name.endsWith("dssd.")) {
				name = name.concat("dssd.");
			}

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
				return;
			}

			DatagramPacket outpacket = new DatagramPacket(response, response.length, 
											inpacket.getAddress(), inpacket.getPort());

			socket.send(outpacket);
		} catch (Exception e) {
			e.printStackTrace();
			exit();
		}
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

		if (shouldForward(query)) {
			return queryRegularDNS(query);
		}

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

		byte rcode = generateAnswer(query, response, flags);

		if (rcode != Rcode.NOERROR && rcode != Rcode.NXDOMAIN) {
			return errorMessage(query, rcode);
		}

		// addAdditional?

		// response.setTSIG(null, Rcode.NOERROR, null);
		return response.toWire(maxLength);
	}

	byte generateAnswer(Message query, Message response, int flags) {
		Record queryRecord = query.getQuestion();
		Name name = queryRecord.getName();
		int type = queryRecord.getType();
		int dclass = queryRecord.getDClass();
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
			// what the hell happened? we already checked for this!
			return Rcode.REFUSED;
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

	boolean shouldForward(Message query) {
		Record queryRecord = query.getQuestion();

		if (queryRecord == null) {
			return true;
		}

		String nameString = queryRecord.getName().toString();
		int type = queryRecord.getType();

		// check for our TLD suffix
		if (!nameString.endsWith("dssd.") && !nameString.endsWith("dssd")) {
			return true;
		}

		if (type != Type.A) {
			return true;
		}

		// we've sanity-checked this record and it's okay for us to handle it!
		// (it is an A record-query belonging to the .dssd. TLD)
		return false;
	}

	// this function forwards the query onto an ACTUAL DNS server and then just
	// returns whatever message it's given
	byte [] queryRegularDNS(Message query) {
		if (server.dns_addrs.isEmpty()) {
			return null;
		}

		InetAddress dns_server = server.dns_addrs.get(0);
		if (dns_server == null) {
			return null;
		}

		try {
			DatagramSocket sock = new DatagramSocket();
			sock.setSoTimeout(10000);

			byte[] sendbuf = query.toWire();
			byte[] recbuf = new byte[1024];

			DatagramPacket sendpack = new DatagramPacket(sendbuf, sendbuf.length, dns_server, 53);
			DatagramPacket recpack = new DatagramPacket(recbuf, recbuf.length);

			sock.send(sendpack);
			sock.receive(recpack);

			byte[] retval = new byte[recpack.getLength()];
			retval = Arrays.copyOf(recbuf, recpack.getLength());

			return retval;
		} catch (SocketTimeoutException e) {
			return null;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
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