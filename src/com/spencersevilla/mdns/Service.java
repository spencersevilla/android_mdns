package com.spencersevilla.mdns;

import java.net.*;
import java.util.Enumeration;

public class Service {
	public String name;
	public int port;
	public InetAddress addr;
	
	public Service(String n, int p, MultiDNS m) {
		name = n;
		port = p;
		addr = m.getAddr();
		
		if (addr == null) {
			addr = Service.generateAddress();
		}

		return;
	}
	
	public static InetAddress generateAddress() {
		try {
	        for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
	 																en.hasMoreElements();) {
	            NetworkInterface intf = en.nextElement();
	            for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses();
															enumIpAddr.hasMoreElements();) {
	                InetAddress inetAddress = enumIpAddr.nextElement();
	                if (!inetAddress.isLoopbackAddress() && !inetAddress.isLinkLocalAddress()
														&& inetAddress.isSiteLocalAddress()) {
	                	return inetAddress;
	                }
	            }
	        }
		} catch (Exception e) {
			e.printStackTrace();
		}

		// couldn't find any address at all???
		try {
			return InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			return null;
		}
	}

	public String toString() {
		return name;
	}
}