package com.sat.satcontorl.utils;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.util.Log;

public class IpUtils {

	public static String getIpAddress(Context context) {
		ConnectivityManager conMann = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		WifiManager wifiManager = (WifiManager) context
				.getSystemService(Context.WIFI_SERVICE);
		String ip = null;

		NetworkInfo mobileNetworkInfo = conMann
				.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
		NetworkInfo wifiNetworkInfo = conMann
				.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

		if (mobileNetworkInfo != null && mobileNetworkInfo.isConnected()) {
			ip = getLocalIpAddress();
		} else if (wifiNetworkInfo != null && wifiNetworkInfo.isConnected()) {

			WifiInfo wifiInfo = wifiManager.getConnectionInfo();
			int ipAddress = wifiInfo.getIpAddress();
			ip = intToIp(ipAddress);
		}

		return ip;
	}

	public static String getHostIP() {

        String hostIp = null;
        try {
            Enumeration nis = NetworkInterface.getNetworkInterfaces();
            InetAddress ia = null;
            while (nis.hasMoreElements()) {
                NetworkInterface ni = (NetworkInterface) nis.nextElement();
                Enumeration<InetAddress> ias = ni.getInetAddresses();
                while (ias.hasMoreElements()) {
                    ia = ias.nextElement();
                    if (ia instanceof Inet6Address) {
                        continue;// skip ipv6
                    }
                    String ip = ia.getHostAddress();
                    if (!"127.0.0.1".equals(ip)) {
                        hostIp = ia.getHostAddress();
                        break;
                    }
                }
            }
        } catch (SocketException e) {
            Log.i("yao", "SocketException");
            e.printStackTrace();
        }
        return hostIp;

    }
	
	public static void takeScreenShot(Context context) {

		// Log.i("IpUtils", "hdb----mSavedPath:"+mSavedPath);
		try {
			// Runtime. getRuntime().exec("su");
			Runtime.getRuntime()
					.exec("screencap -p " + context.getFilesDir()
							+ "/screenshot.png");
			// Runtime.
			// getRuntime().exec("chmod 777 "+context.getFilesDir()+"screenshot.png");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// instanceof Inet4Address"

	public static String getLocalIpAddress() {
		try {
			String ipv4;
			ArrayList<NetworkInterface> nilist = Collections
					.list(NetworkInterface.getNetworkInterfaces());
			for (NetworkInterface ni : nilist) {
				ArrayList<InetAddress> ialist = Collections.list(ni
						.getInetAddresses());
				for (InetAddress address : ialist) {
					if ((!address.isLoopbackAddress())
							&& (address instanceof Inet4Address)) {
						return address.getHostAddress();
					}
				}

			}

		} catch (SocketException ex) {
			Log.e("localip", ex.toString());
		}
		return null;
	}

	public static String intToIp(int ipInt) {
		StringBuilder sb = new StringBuilder();
		sb.append(ipInt & 0xFF).append(".");
		sb.append((ipInt >> 8) & 0xFF).append(".");
		sb.append((ipInt >> 16) & 0xFF).append(".");
		sb.append((ipInt >> 24) & 0xFF);
		return sb.toString();
	}

	public static InetAddress getBroadcastAddress(Context context)
			throws UnknownHostException {
		WifiManager wifi = (WifiManager) context
				.getSystemService(Context.WIFI_SERVICE);
		DhcpInfo dhcp = wifi.getDhcpInfo();
		if (dhcp == null) {
			return InetAddress.getByName("255.255.255.255");
		}
		/*int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
		byte[] quads = new byte[4];
		for (int k = 0; k < 4; k++) {
			quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
		}

		return InetAddress.getByAddress(quads);*/
		String hostIP = getHostIP();
		if (hostIP != null && !hostIP.equals("")) {
             String substring = hostIP.substring(0, hostIP.lastIndexOf(".") + 1);
             Log.i("123", "hdb------substring:"+substring);
             return InetAddress.getByName(substring+"255");
        }
		return null;
	//	 return InetAddress.getByName("192.168.43.255");
	}
	

	public static MulticastLock openWifiBrocast(Context context) {
		WifiManager wifiManager = (WifiManager) context
				.getSystemService(Context.WIFI_SERVICE);
		MulticastLock multicastLock = wifiManager
				.createMulticastLock("MediaRender");
		if (multicastLock != null) {
			multicastLock.acquire();
		}
		return multicastLock;
	}
}
