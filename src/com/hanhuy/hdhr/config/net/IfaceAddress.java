package com.hanhuy.hdhr.config.net;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.InterfaceAddress;
import java.net.UnknownHostException;

public interface IfaceAddress {
    InetAddress getAddress();
    InetAddress getBroadcast();

    public static class Factory {
        static Pattern ADDR_PATTERN = Pattern.compile(
                "\\s*inet\\s*(\\d+\\.\\d+\\.\\d+\\.\\d+)" +
                "\\s*netmask\\s*\\w+" +
                "\\s*broadcast\\s*(\\d+\\.\\d+\\.\\d+\\.\\d+)\\s*");

        public static List<IfaceAddress> getIfaceAddresses()
        throws SocketException {

            try {
                Class.forName("java.net.InterfaceAddress");
                ArrayList<IfaceAddress> addrs = new ArrayList<IfaceAddress>();
                Enumeration<NetworkInterface> nifs =
                        NetworkInterface.getNetworkInterfaces();
                while (nifs.hasMoreElements()) {
                    NetworkInterface nif = nifs.nextElement();
                    List<InterfaceAddress> iaddrs = nif.getInterfaceAddresses();
                    for (final InterfaceAddress addr : iaddrs) {
                        addrs.add(new IfaceAddress() {
                            public InetAddress getAddress() {
                                return addr.getAddress();
                            }
                            public InetAddress getBroadcast() {
                                return addr.getBroadcast();
                            }
                        });
                    }
                }
                return addrs;
            }
            catch (ClassNotFoundException e) {
                return getIfaceAddressesFallback();
            }
        }

        public static List<IfaceAddress> getIfaceAddressesFallback()
        throws SocketException {
            ArrayList<IfaceAddress> addrs = new ArrayList<IfaceAddress>();
            String os = System.getProperty("os.name");
            if (!os.startsWith("Mac OS"))
                throw new UnsupportedOperationException("use java6");
            ProcessBuilder pb = new ProcessBuilder(
                    "/sbin/ifconfig", "-a", "inet");
            pb.redirectErrorStream(true);
            Process p;
            try {
                p = pb.start();
            }
            catch (IOException e) {
                e.printStackTrace();
                throw new SocketException(e.getMessage());
            }

            BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream()));
            String line;
            try {
                while ((line = r.readLine()) != null) {
                    final Matcher m = ADDR_PATTERN.matcher(line);
                    if (!m.matches())
                        continue;
                    addrs.add(new IfaceAddress() {
                        public InetAddress getAddress() {
                            try {
                                return InetAddress.getByName(m.group(1));
                            }
                            catch (UnknownHostException e) {
                                throw new IllegalStateException(e);
                            }
                        }
                        public InetAddress getBroadcast() {
                            try {
                                return InetAddress.getByName(m.group(2));
                            }
                            catch (UnknownHostException e) {
                                throw new IllegalStateException(e);
                            }
                        }
                    });
                }
            }
            catch (IOException e) {
                e.printStackTrace();
                throw new SocketException(e.getMessage());
            }
            return addrs;
        }
    }
}
