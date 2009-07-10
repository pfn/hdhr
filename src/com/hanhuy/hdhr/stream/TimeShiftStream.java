package com.hanhuy.hdhr.stream;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;

public class TimeShiftStream
implements PacketSource, PacketListener, Runnable {
    private TimeShiftRingBuffer inbuffer;
    private VideoDataPacketRingBuffer outbuffer;
    private volatile boolean realtime = true;
    private volatile boolean shutdown;
    private volatile boolean paused;
    private boolean pausing;

    private long inPCR;
    private long outPCR;
    private long basePCR;
    private final static ThreadLocal<TSPacket> tspacket =
            new ThreadLocal<TSPacket>();
    private final static int VALID_PID_MASK = 0x1ffe;
    private short pmtPID = -1;
    private short pcrPID = -1;

    private boolean hasVideo;
    private boolean hasAudio;

    private Map<Short,Short> pat;

    private long[] pcr    = { -1, -1 };
    private int[]  pcrpos = { -1, -1 };

    private ArrayList<TimeShiftListener> tsListeners =
            new ArrayList<TimeShiftListener>();
    private ArrayList<PacketListener> listeners =
            new ArrayList<PacketListener>();

    public TimeShiftStream() {
        outbuffer = new VideoDataPacketRingBuffer(2 * 1024 * 1024);
        try {
            File tsbuf = new File(System.getProperty("user.home"), ".hdhrb-timeshift-buffer.ts");
            tsbuf.deleteOnExit();
            inbuffer = new TimeShiftRingBuffer(this,
                    tsbuf, 2L * 1024 * 1024 * 1024);
        }
        catch (IOException e) {
            throw new IllegalStateException(e);
        }

        Thread t = new Thread(this, "TimeShiftStream");
        t.start();
    }
    public void packetArrived(PacketEvent e) throws IOException {
        long p = processPacket(e.packet, true, realtime);

        if (p != -1)
            inbuffer.setCurrentPCR(p);
        inbuffer.add(e.packet);

        synchronized(this) {
            if (realtime)
                firePacketArrived(e);
        }
    }

    private static TSPacket getTSPacket() {
        TSPacket tsp = tspacket.get();
        if (tsp == null) {
            tsp = new TSPacket();
            tspacket.set(tsp);
        }
        return tsp;
    }

    public long processPacket(byte[] p, boolean in, boolean out) {
        return processPacket(ByteBuffer.wrap(p), in, out);
    }
    public long processPacket(ByteBuffer p, boolean in, boolean out) {
        long pcr = -1;

        TSPacket tspacket = getTSPacket();

        for (int i = 0; i < RTPProxy.TS_PACKET_PER_DATA_PACKET; i++) {
            p.position(i * TSPacket.PACKET_SIZE);
            p.limit(p.position() + TSPacket.PACKET_SIZE);
            boolean valid = tspacket.parse(p.slice());
            if (!valid)
                return -2; // data not ready
            if (pat == null) {
                if (tspacket.isPAT()) {
                    pat = tspacket.getPAT();
                    if (pat.size() != 1) {
                        throw new IllegalStateException(
                                "Stream should only have 1 program, found " +
                                pat.size());
                    }
                    for (Short pid : pat.values())
                        pmtPID = pid;
                }
            }
            if (pcrPID == -1) {
                if (pmtPID == tspacket.getPID() && tspacket.isPMT()) {
                    pcrPID = tspacket.getPCRPID();
                    if (pcrPID == TSPacket.NULL_PID) {
                        throw new IllegalStateException(
                                "TS does not specify PCR");
                    }
                    hasAudio = tspacket.hasAudioStream();
                    hasVideo = tspacket.hasVideoStream();
                    Map<Short,Byte> pmt = tspacket.getPMT();
                    for (Short epid : pmt.keySet()) {
                        System.out.printf("PID 0x%04x - Type 0x%02x\n",
                                epid, pmt.get(epid) & 0xff);
                    }
                }
            }
            if ((pcrPID & VALID_PID_MASK) != 0) {
                if (pcrPID == tspacket.getPID()) {
                    long _pcr = tspacket.getPCR();
                    if (_pcr != -1) {
                        pcr = _pcr;
                        if (in) {
                            inPCR = pcr; // 27MHz to milliseconds
                            if (basePCR == 0) {
                                basePCR = inPCR;
                                inbuffer.setStartPCR(basePCR);
                            }
                        }
                        if (out)
                            outPCR = pcr;
                    }
                }
            }
        }
        return pcr;
    }

    public boolean isClosed() {
        return shutdown;
    }

    public boolean isRealTime() {
        return realtime;
    }
    public synchronized void close() throws IOException {
        shutdown = true;
        notify();
        synchronized (listeners) {
            for (Iterator<PacketListener> i = listeners.iterator();
                    i.hasNext();) {
                PacketListener l = i.next();
                l.close();
                i.remove();
            }
        }
        inbuffer.close();
    }

    public void addPacketListener(PacketListener l) {
        synchronized (listeners) {
            listeners.add(l);
        }
    }

    public void addTimeShiftListener(TimeShiftListener l) {
        synchronized (tsListeners) {
            tsListeners.add(l);
        }
    }

    private void fireTimeShiftEvent(TimeShiftEvent e) {
        if (shutdown) return;

        for (TimeShiftListener l : tsListeners) {
            switch (e.type) {
            case PAUSE:
                l.timePaused(e);
                break;
            case RESUME:
                l.timeResumed(e);
                break;
            case SHIFT:
                l.timeShifted(e);
                break;
            default:
                throw new IllegalArgumentException(
                        e.type == null ? "null type" : e.type.toString());
            }
        }
    }

    private void firePacketArrived(byte[] packet) {
        PacketEvent e = new PacketEvent(this, packet);
        firePacketArrived(e);
    }
    private void firePacketArrived(PacketEvent e) {
        if (shutdown) return;

        synchronized (listeners) {
            for (Iterator<PacketListener> i = listeners.iterator();
                    i.hasNext();) {
                PacketListener l = i.next();
                if (l.isClosed())
                    i.remove();
                else {
                    try {
                        l.packetArrived(e);
                    }
                    catch (IOException ex) {
                        ex.printStackTrace();
                        try {
                            l.close();
                        }
                        catch (IOException ex2) { }
                    }
                }
            }
        }
    }

    /**
     * @return the number of seconds off the current time
     */
    public int getShiftDelta() {
        if (realtime) return 0;
        return (int) (inPCR - outPCR) / 27000000;
    }

    /**
     * @param seconds number of seconds to shift forward or backward (negative)
     * @return actual number of seconds shifted
     */
    public synchronized int shift(int seconds) {
        long oldpcr = outPCR;
        long newpcr = seek(outPCR + (seconds * 27000000));
        return (int) Math.round((double) (newpcr - oldpcr) / 27000000);
    }

    public synchronized long seek(long targetpcr) {
        System.out.println("**** SEEKING!");
        paused   = true;
        realtime = false;
        notify();
        while (!pausing) {
            try {
                wait();
            }
            catch (InterruptedException e) {
                if (shutdown) break;
            }
        }
        synchronized (outbuffer) {

            long newpcr = inbuffer.seek(targetpcr);
            if (newpcr == -1) {
                now();
                return inPCR;
            }
            pcrpos[0] = pcrpos[1] = -1;
            outbuffer.clear();

            paused = false;
            notify();

            fireTimeShiftEvent(
                    new TimeShiftEvent(this, TimeShiftEvent.EventType.SHIFT));
            return newpcr;
        }
    }

    public synchronized void pause() {
        System.out.println("**** PAUSING!");
        paused = true;
        synchronized (outbuffer) {
            if (realtime) {
                inbuffer.seek(TimeShiftRingBuffer.SEEK_NOW);
                pcrpos[0] = pcrpos[1] = -1;
                outbuffer.clear();
            }
            realtime = false;
            notify();
        }
        fireTimeShiftEvent(
                new TimeShiftEvent(this, TimeShiftEvent.EventType.PAUSE));
    }

    public synchronized void resume() {
        System.out.println("**** RESUMING!");
        paused = false;
        notify();
        fireTimeShiftEvent(
                new TimeShiftEvent(this, TimeShiftEvent.EventType.RESUME));
    }

    public synchronized void base() {
        paused   = true;
        realtime = false;
        notify();
        while (!pausing) {
            try {
                wait();
            }
            catch (InterruptedException e) {
                if (shutdown) break;
            }
        }
        synchronized (outbuffer) {
            inbuffer.seek(inbuffer.getStartPCR());
            pcrpos[0] = pcrpos[1] = -1;
            outbuffer.clear();

            paused = false;
            notify();

            fireTimeShiftEvent(
                    new TimeShiftEvent(this, TimeShiftEvent.EventType.SHIFT));
        }
    }

    public synchronized void now() {
        if (realtime) return;

        realtime = true;
        paused   = false;
        notify();
        fireTimeShiftEvent(
                new TimeShiftEvent(this, TimeShiftEvent.EventType.SHIFT));
    }

    private boolean fillBuffer(TimeShiftRingBuffer in,
            VideoDataPacketRingBuffer out, byte[] packet) {
        if (pcrpos[0] == -1) {
            pcrpos[0] = pcrpos[1];
            pcr[0]    = pcr[1];
            pcrpos[1] = -1;
            pcr[1]    = -1;
        }
        boolean filled = pcrpos[0] == -1 || pcrpos[1] == -1;
        while (pcrpos[0] == -1 || pcrpos[1] == -1) {
            in.nextPacket(packet);
            out.add(packet);

            long pcrN = processPacket(packet, false, true);
            if (pcrN == -2) {
                filled = false;
                break;
            } else if (pcrN != -1) {
                if (pcrpos[0] == -1) {
                    pcrpos[0] = out.available() - 1;
                    pcr[0] = pcrN;
                } else {
                    pcrpos[1] = out.available() - 1;
                    pcr[1] = pcrN;
                }
            }
        }
        return filled;
    }

    public void run() {
        long totalus  = 0;
        long totalpcr = 0;
        while (!shutdown) {
            synchronized(this) {
                while ((paused || realtime) && !shutdown) {
                    try {
                        if (paused && totalpcr != 0)
                            System.out.printf("TS lag: %dus\n",
                                    totalus - totalpcr);
                        if (paused)
                            pausing = true;
                        notify();
                        wait();
                    }
                    catch (InterruptedException e) {
                    }
                }
                if (!paused)
                    pausing = false;
            }
            byte[] packet = new byte[RTPProxy.VIDEO_DATA_PACKET_SIZE];
            long elapsed = 0;
            int _usRate  = 0; // no delay until pcr is in the first slot
            totalus  = 0;
            totalpcr = 0;

            while (!realtime && !paused && !shutdown) {
                long ns = System.nanoTime();
                int usRate;
                synchronized (outbuffer) {
                    fillBuffer(inbuffer, outbuffer, packet);
                    if (pcrpos[0] == 0) {
                        long usDelta = (pcr[1] - pcr[0]) / 27;
                        _usRate = (int) usDelta / pcrpos[1];
                    }
                    totalpcr += _usRate;
                    usRate    = _usRate;

                    if (paused) break;
                    outbuffer.nextPacket(packet);
                }

                firePacketArrived(packet);

                usRate -= elapsed; // only sleep enough to parity with pcr

                if (usRate > 0)
                    sleep(usRate);

                pcrpos[0]--;
                pcrpos[1]--;

                totalus += (System.nanoTime() - ns) / 1000;

                if (totalpcr < totalus)
                    elapsed = totalus - totalpcr;
                else
                    elapsed = 0;
            }
        }
        tspacket.set(null);
    }

    private void sleep(long us) {
        try {
            Thread.sleep(us / 1000, (int) (us % 1000) * 1000);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return listeners.toString();
    }

    public long getCurrentPCR() {
        return outPCR;
    }

    public long getStartPCR() {
        return inbuffer.getStartPCR();
    }

    public long getInputPCR() {
        return inPCR;
    }
}
