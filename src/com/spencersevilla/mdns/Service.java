package com.spencersevilla.mdns;

import java.net.*;
import java.util.Enumeration;

public class Service {
	public String name;
	public int port;
	public String addr;
	
	public Service(String n, int p, MultiDNS m) {
		name = n;
		port = p;
		addr = m.getAddr();
		
		if (addr == null) {
			try {
		        for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
		 																en.hasMoreElements();) {
		            NetworkInterface intf = en.nextElement();
		            for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses();
																enumIpAddr.hasMoreElements();) {
		                InetAddress inetAddress = enumIpAddr.nextElement();
		                if (!inetAddress.isLoopbackAddress() && !inetAddress.isLinkLocalAddress()
															&& inetAddress.isSiteLocalAddress()) {
		                	addr = inetAddress.getHostAddress();
							return;
		                }
		            }
		        }
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		// no IP address at all???
		if (addr == null) {
			addr = "127.0.0.1";
		}
		return;
	}
	
	public String toString() {
		return name;
	}
}