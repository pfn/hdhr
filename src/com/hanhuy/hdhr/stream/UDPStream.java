package com.hanhuy.hdhr.stream;

import java.io.IOException;
import java.net.SocketException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.UnknownHostException;

public class UDPStream implements PacketListener {

    private int remotePort;
    private DatagramSocket ds;
    private InetAddress localhost;
    public UDPStream() throws SocketException, IOException {
        ds = new DatagramSocket();
        
        try {
            localhost = InetAddress.getByAddress(new byte[] { 127, 0, 0, 1 });
        }
        catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }

        DatagramSocket remote = new DatagramSocket(
                new InetSocketAddress(localhost, 0));
        remotePort = remote.getLocalPort();
        remote.close();
    }

    public void close() {
        if (ds != null)
            ds.close();
        ds = null;
    }

    public boolean isClosed() {
        return ds == null;
    }

    public void packetArrived(PacketEvent e) throws IOException {
        byte[] packet = e.packet;
        if (ds == null)
            return;
        ds.send(new DatagramPacket(
                packet, packet.length, localhost, remotePort));
    }

    public int getRemotePort() {
        return remotePort;
    }

    @Override
    public String toString() {
        return "udp://@localhost:" + getRemotePort();
    }
}
