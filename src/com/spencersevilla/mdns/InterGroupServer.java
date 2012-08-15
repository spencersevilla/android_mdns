/*  This server is group-agnostic and serves one function: to pass "translate" requests
 *  when they must go to other nodes (such as using the ChordGroup). It is the primary
 *  public interface for a mdns server to request service-resolution from another computer.
 */

package com.spencersevilla.mdns;

import java.io.*;
import java.net.*;
import java.util.*;

public class InterGroupServer implements Runnable {
	protected MultiDNS mdns;
	private Thread thread;
	public static int port = 5300;
    private ServerSocket socket;
	private boolean listening;
	private volatile boolean running;
	private ArrayList<InterGroupThread> threads;

	InterGroupServer(MultiDNS m) throws Exception {
		mdns = m;
		threads = new ArrayList<InterGroupThread>();
		socket = new ServerSocket(port);

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

	public void run() {
        byte buf[];
		byte buf2[];
        DatagramPacket pack;
		DatagramPacket pack2;

        try {
			listening = true;

			while(listening) {				
		        Socket skt = socket.accept();

		        if (!running) {
		        	continue;
		        }

				InterGroupThread t = new InterGroupThread(skt, mdns, threads);
				threads.add(t);
				t.start();
			}
        	socket.close();

 		} catch (Exception e) {
            e.printStackTrace();
        }
	}
	
	public String resolveService(String servicename, int score, String addr, int port) {
		try {
			Socket sock = new Socket(addr, port);
			DataInputStream is = new DataInputStream(sock.getInputStream());
			PrintStream os = new PrintStream(sock.getOutputStream());
		
			String request = ("RESOLVE::" + servicename + ":" + score);
			os.println(request);
		
			System.out.println("IGS: sent request: " + request + "to " + addr + ":" + port);
		
			String response = is.readLine();
		
			System.out.println("IGS: received response: " + response);
		
			String[] args = response.split("::");
			if (args.length < 2) {
				System.err.println("IGS resolveService: incorrect number of args! " + response);
				return null;
			}
		
			if (!args[0].equals("RESPONSE")) {
				System.err.println("IGS resolveService: incorrect command! " + response);
				return null;
			}
		
			if (args[1].equals("FAILURE")) {
				return null;
			}
		
			sock.close();
			return args[1];
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
}

class InterGroupThread extends Thread {
	Socket socket = null;
    MultiDNS mdns = null;
	ArrayList<InterGroupThread> array = null;
	
    public InterGroupThread(Socket s, MultiDNS m, ArrayList<InterGroupThread> ar) {
		socket = s;
        mdns = m;
		array = ar;
	}

    public void run() {
		try {
			DataInputStream is = new DataInputStream(socket.getInputStream());
			PrintStream os = new PrintStream(socket.getOutputStream());
			String response = "RESPONSE::FAILURE";
		
			String request = is.readLine();
		
			System.out.println("IGS: received request " + request);
		
			String[] args = request.split("::");
			if (args.length < 2) {
				System.err.println("IGS error: incorrect number of args! " + request);
				exit();
			}
		
			if (!args[0].equals("RESOLVE")) {
				System.err.println("IGS error: incorrect command! " + request);
				exit();
			}
		
			String[] values = args[1].split(":");
			if (values.length < 2) {
				System.err.println("IGS error: incorrect number of values! " + request);
				exit();
			}
		
			String servicename = values[0];
			int score = Integer.parseInt(values[1]);
		
			String addr = mdns.forwardRequest(servicename, score);
			if (addr != null) {
				response = "RESPONSE::" + addr;
			}
		
			os.println(response);
			
			System.out.println("IGS: sent response " + response);
			
			exit();
		} catch (Exception e) {
			e.printStackTrace();
			array.remove(this);
		}
	}

	public void exit() throws Exception {
		socket.close();
		array.remove(this);
	}
}