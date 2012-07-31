package multiDNS;

import java.net.*;

public class Service {
	String name;
	int port;
	String addr;
	
	Service(String n, int p) {
		name = n;
		port = p;
		try {
			addr = InetAddress.getLocalHost().getHostAddress();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public String toString() {
		return name;
	}
}