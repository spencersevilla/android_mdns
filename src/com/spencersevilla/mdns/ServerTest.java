package com.spencersevilla.mdns;

import java.io.*;
import java.net.*;

public class ServerTest {
    public static void main(String[] args) {
        new ServerTest();
    }
    
    ServerTest() {
        int port = 6363;
        String group = "224.4.5.6";
		String message = "FLD_REQ:testserver";
        byte buf[] = message.getBytes();
		byte buf2[] = new byte[1024];
        int ttl = 1;
        
        try {
            
            MulticastSocket s = new MulticastSocket();
            s.setSoTimeout(1000);

            DatagramPacket pack = new DatagramPacket(buf, buf.length, InetAddress.getByName(group), port);
            DatagramPacket pack2 = new DatagramPacket(buf2, buf2.length);

            s.send(pack);
			s.receive(pack2);
			// forks for timeout here
            s.close();
			String st = new String(pack2.getData());
			System.out.println("resolved service! received: " + st);

		} catch (InterruptedIOException iioe) {
			System.out.println("could not resolve");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
