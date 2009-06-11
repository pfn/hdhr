package com.hanhuy.hdhr.config;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.channels.DatagramChannel;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EventListener;
import java.util.EventObject;
import java.util.ArrayList;
import java.util.Iterator;

public class RTPProxy implements Runnable {
    private DatagramChannel dc;
    private short rtp_sequence = (short) 0xffff;
    private byte[] sequence = new byte[0x2000];

    private int sequenceErrorCount;
    private int transportErrorCount;
    private int networkErrorCount;
    private int packetCount;

    private final ArrayList<PacketListener> listeners =
            new ArrayList<PacketListener>();

    private volatile boolean shutdown;

    private final static int TS_PACKET_SIZE = 188;
    private final static int VIDEO_RTP_DATA_PACKET_SIZE = ((188 * 7) + 12);
    private final static int VIDEO_DATA_PACKET_SIZE = (188 * 7);

    private Thread t;

    public RTPProxy(InetAddress bindHost) throws IOException, SocketException {
        dc = DatagramChannel.open();
        dc.socket().setSoTimeout(1000);
        dc.socket().bind(new InetSocketAddress(bindHost, 0));

        Arrays.fill(sequence, (byte) 0xff);
        t = new Thread(this, "RTP Proxy :" + getLocalPort());
        t.setPriority(Thread.MAX_PRIORITY);
        t.start();
    }

    public void run() {
        try {
            receiveLoop();
        }
        catch (IOException e) {
            if (!shutdown)
                throw new RuntimeException(e);
        }
    }

    private void checkRTP(ByteBuffer buf) {
        buf.position(buf.position() + 2);
        short rtp_seq = buf.getShort();
        buf.position(buf.position() + 8);
        
        if (rtp_seq != (rtp_sequence + 1)) {
            if (rtp_sequence != (short) 0xffff) {
                networkErrorCount++;

                Arrays.fill(sequence, (byte) 0xff);
            }
        }

        rtp_sequence = rtp_seq;
    }

    private void checkTS(byte[] packet, int offset) {
        int b1 = ((packet[offset + 1] & 0x1f) << 8) & 0xffff;
        int b2 = packet[offset + 2] & 0xff;
        int pktID = (b1 | b2) & 0xffff;

        if (pktID == 0x1fff)
            return;

        if (((packet[offset + 1] >> 7) & 0xff) != 0) {
            transportErrorCount++;
            sequence[pktID] = (byte) 0xff;
            return;
        }

        byte continuityCounter = (byte) (packet[offset + 3] & 0x0f);
        byte lastSeq = sequence[pktID];

        if (continuityCounter == ((lastSeq + 1) & 0x0f)) {
            sequence[pktID] = continuityCounter;
            return;
        }

        if (lastSeq == (byte) 0xff) {
            sequence[pktID] = continuityCounter;
            return;
        }

        if (continuityCounter == lastSeq)
            return;

        sequenceErrorCount++;
        sequence[pktID] = continuityCounter;
    }

    private void receiveLoop() throws IOException {
        ByteBuffer buf = ByteBuffer.allocateDirect(VIDEO_RTP_DATA_PACKET_SIZE);
        while (!shutdown) {

            buf.clear();
            try {
                dc.receive(buf);
            }
            catch (SocketTimeoutException e) {
                // ignore, continue
                continue;
            }
            buf.flip();


            if (buf.remaining() == VIDEO_RTP_DATA_PACKET_SIZE) {
                checkRTP(buf);
            }

            if (buf.remaining() != VIDEO_DATA_PACKET_SIZE) {
                if (buf.remaining() > 0) {
                    System.err.println("Invalid data received, " +
                            "len != VIDEO_DATA_PACKET_SIZE: " +
                            buf.remaining() + " != " + VIDEO_DATA_PACKET_SIZE);
                    continue; // ignore invalid data
                }
                shutdown = true;
            }

            packetCount++;
            byte[] packet = new byte[buf.remaining()];
            buf.get(packet);

            for (int i = 0; i < 7; i++)
                checkTS(packet, TS_PACKET_SIZE * i);
            firePacketEvent(packet);
        }
    }

    public int getLocalPort() {
        return dc.socket().getLocalPort();
    }

    public void close() {
        shutdown = true;
        t.interrupt();
        try {
            dc.close();
        }
        catch (IOException e) { }

        synchronized (listeners) {
            for (Iterator<PacketListener> i = listeners.iterator();
                    i.hasNext();) {
                PacketListener l = i.next();
                i.remove();
                try {
                    l.close();
                }
                catch (IOException e) { }
            }
        }
    }

    private void firePacketEvent(byte[] packet) throws IOException {
        PacketEvent e = new PacketEvent(packet);
        if (shutdown) return;
        synchronized (listeners) {
            for (PacketListener l : listeners)
                l.packetArrived(e);
        }
    }
    public void addPacketListener(PacketListener l) {
        listeners.add(l);
    }

    public interface PacketListener extends EventListener, Closeable {
        public void packetArrived(PacketEvent e) throws IOException;
    }

    public class PacketEvent extends EventObject {
        public final byte[] packet;
        PacketEvent(byte[] packet) {
            super(RTPProxy.this);
            this.packet = packet;
        }
    }

    public int getPacketCount() {
        return packetCount;
    }

    public int getErrorsCount() {
        return networkErrorCount + transportErrorCount + sequenceErrorCount;
    }

    public int getNetworkErrorCount() {
        return networkErrorCount;
    }

    public int getTransportErrorCount() {
        return transportErrorCount;
    }

    public int getSequenceErrorCount() {
        return sequenceErrorCount;
    }
}
