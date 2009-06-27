package com.hanhuy.hdhr.stream;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.channels.DatagramChannel;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Iterator;

public class RTPProxy implements Runnable, PacketSource {
    private DatagramChannel dc;
    private short rtp_sequence = (short) 0xffff;
    /**
     * valid PID values are 0x0000 to 0x1ffe
     */
    private byte[] sequence = new byte[0x1ffe];

    private int sequenceErrorCount;
    private int transportErrorCount;
    private int networkErrorCount;
    private int packetCount;
    private long byteCount;

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
        int pid = (b1 | b2) & 0xffff;

        if (pid == 0x1fff)
            return;

        // 0x80 = TEI
        if ((packet[offset + 1] & 0x80) != 0) {
            transportErrorCount++;
            sequence[pid] = (byte) 0xff;
            return;
        }

        byte continuityCounter = (byte) (packet[offset + 3] & 0x0f);
        byte lastSeq = sequence[pid];

        if (continuityCounter == ((lastSeq + 1) & 0x0f)) {
            sequence[pid] = continuityCounter;
            return;
        }

        if (lastSeq == (byte) 0xff) {
            sequence[pid] = continuityCounter;
            return;
        }

        if (continuityCounter == lastSeq)
            return;

        sequenceErrorCount++;
        sequence[pid] = continuityCounter;
    }

    private void receiveLoop() throws IOException {
        ByteBuffer buf = ByteBuffer.allocateDirect(VIDEO_RTP_DATA_PACKET_SIZE);
        byte[] packet = new byte[VIDEO_DATA_PACKET_SIZE];
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
            byteCount += buf.remaining();
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
        PacketEvent e = new PacketEvent(this, packet);
        if (shutdown) return;
        synchronized (listeners) {
            for (Iterator<PacketListener> i = listeners.iterator();
                    i.hasNext();) {
                PacketListener l = i.next();
                if (l.isClosed())
                    i.remove();
                else
                    l.packetArrived(e);
            }
        }
    }
    public void clearPacketListeners() {
        synchronized (listeners) {
            listeners.clear();
        }
    }
    /**
     * Returns a read-only list of listeners
     */
    public List<PacketListener> getPacketListeners() {
        return Collections.unmodifiableList(listeners);
    }

    public void addPacketListener(PacketListener l) {
        synchronized (listeners) {
            listeners.add(l);
        }
    }

    public long getByteCount() {
        return byteCount;
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
