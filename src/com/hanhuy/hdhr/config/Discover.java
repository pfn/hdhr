package com.hanhuy.hdhr.config;

import java.io.IOException;
import java.net.NetworkInterface;
import java.net.InterfaceAddress;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.SocketException;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Set;

public class Discover {

    public static void discover() throws SocketException, IOException {
        Enumeration<NetworkInterface> ifaces =
                NetworkInterface.getNetworkInterfaces();

        List<DatagramSocket> socks = new ArrayList<DatagramSocket>();
        Map<DatagramSocket,InetAddress> broadcastMap =
                new HashMap<DatagramSocket,InetAddress>();
        while (ifaces.hasMoreElements()) {
            NetworkInterface iface = ifaces.nextElement();
            List<InterfaceAddress> addrs = iface.getInterfaceAddresses();
            for (InterfaceAddress addr : addrs) {
                if (addr.getBroadcast() == null)
                    continue;
                DatagramChannel c = DatagramChannel.open();
                DatagramSocket sock = c.socket();
                sock.setSoTimeout(1000);
                sock.setBroadcast(true);
                sock.bind(new InetSocketAddress(addr.getAddress(), 0));
                socks.add(sock);
                broadcastMap.put(sock, addr.getBroadcast());
            }
        }



        Packet packet = new Packet();
        packet.addTag(Packet.Tag.DEVICE_TYPE, Packet.DEVICE_TYPE_TUNER);
        packet.addTag(Packet.Tag.DEVICE_ID,   Packet.DEVICE_ID_WILDCARD);
        byte[] b = packet.seal(Packet.Type.DISCOVER_REQ);
        for (DatagramSocket s : socks) {
            DatagramPacket p = new DatagramPacket(b, b.length,
                    broadcastMap.get(s), Packet.DISCOVER_UDP_PORT);
            s.send(p);
            s.getChannel().configureBlocking(false);
        }

        Selector select = Selector.open();
        for (DatagramSocket s : socks) {
            DatagramChannel c = s.getChannel();
            c.register(select, SelectionKey.OP_READ);
        }

        int results = select.select(1000);
        Set<SelectionKey> keys = select.selectedKeys();
        System.out.println("Results: " + results);
        for (SelectionKey key : keys) {
            DatagramChannel c = (DatagramChannel) key.channel();
            ByteBuffer bb = ByteBuffer.allocate(3074);
            InetSocketAddress peer = (InetSocketAddress) c.receive(bb);
            bb.flip();
            Packet p = new Packet(bb);
            for (Packet.TagEntry entry : p) {
                System.out.println(entry.tag);
                int id = ByteBuffer.wrap(entry.value).getInt();
                System.out.println(Integer.toHexString(id));
            }
        }
    }

    public static void main(String[] args) throws Exception {
        discover();
    }
}
