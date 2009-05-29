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
import java.util.Collections;

public class Discover {

    private final static Map<Integer,InetAddress[]> devices =
            new HashMap<Integer,InetAddress[]>();
    public static Map<Integer,InetAddress[]> discover() throws TunerException {
        return discover(Packet.DEVICE_ID_WILDCARD);
    }

    /**
     * @return a Map containing device id mapped to an InetAddress array:
     *         element 0 = interface address, element 1 = device address
     */
    public static Map<Integer,InetAddress[]> discover(int deviceId)
    throws TunerException {
        if (devices.containsKey(deviceId))
            return Collections.singletonMap(deviceId, devices.get(deviceId));

        try {
            Enumeration<NetworkInterface> ifaces =
                    NetworkInterface.getNetworkInterfaces();
    
            List<DatagramChannel> socks = new ArrayList<DatagramChannel>();
            Map<DatagramChannel,InterfaceAddress> ifaceMap =
                    new HashMap<DatagramChannel,InterfaceAddress>();
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
                    socks.add(c);
                    ifaceMap.put(c, addr);
                }
            }
    
            Packet packet = new Packet();
            packet.addTag(Packet.Tag.DEVICE_TYPE, Packet.DEVICE_TYPE_TUNER);
            packet.addTag(Packet.Tag.DEVICE_ID,   deviceId);
    
            ByteBuffer b = packet.seal(Packet.Type.DISCOVER_REQ);
    
            for (DatagramChannel c : socks) {
                b.clear();
                c.send(b, new InetSocketAddress(ifaceMap.get(c).getBroadcast(),
                        Packet.DISCOVER_UDP_PORT));
                c.configureBlocking(false);
            }
    
            Selector select = Selector.open();
            long timeout = System.currentTimeMillis() + 250;
            for (DatagramChannel c : socks) {
                c.register(select, SelectionKey.OP_READ);
            }
            Map<Integer,InetAddress[]> deviceMap =
                    new HashMap<Integer,InetAddress[]>();

            while (System.currentTimeMillis() < timeout) {
                int results = select.select(250);
                Set<SelectionKey> keys = select.selectedKeys();
                Packet p = new Packet();
                for (SelectionKey key : keys) {
                    DatagramChannel c = (DatagramChannel) key.channel();
                    ByteBuffer buf = p.buffer();
                    InetSocketAddress peer = (InetSocketAddress) c.receive(buf);
                    if (buf.position() == 0) continue;
                    p.parse();
                    if (p.getType() != Packet.Type.DISCOVER_RPY)
                        throw new TunerException(
                               "Unexpected response: " + p.getType());
        
                    Map<Packet.Tag,byte[]> tags = p.getTagMap();
                    int id = ByteBuffer.wrap(
                            tags.get(Packet.Tag.DEVICE_ID)).getInt();
                    if (deviceId != Packet.DEVICE_ID_WILDCARD
                    &&  deviceId != id) {
                            throw new TunerException(
                                "Did not get expected device id: " + id);
                    }
                    if (!deviceMap.containsKey(id)) {
                        deviceMap.put(id, new InetAddress[] {
                                ifaceMap.get(c).getAddress(),
                                peer.getAddress() });
                        timeout = System.currentTimeMillis() + 250;
                    }
                }
                if (deviceId != Packet.DEVICE_ID_WILDCARD)
                    devices.put(deviceId, deviceMap.get(deviceId));
            }
            return deviceMap;
        }
        catch (SocketException e) {
            throw new TunerException(e.getMessage(), e);
        }
        catch (IOException e) {
            throw new TunerException(e.getMessage(), e);
        }
    }

    public static void main(String[] args) throws Exception {
        Map<Integer,InetAddress[]> deviceMap = discover(
                Packet.DEVICE_ID_WILDCARD);
        for (Map.Entry<Integer,InetAddress[]> device : deviceMap.entrySet()) {
            System.out.println("Found device " +
                    Integer.toHexString(device.getKey()) + " at " +
                    device.getValue()[1].getHostAddress());
        }
    }
}
