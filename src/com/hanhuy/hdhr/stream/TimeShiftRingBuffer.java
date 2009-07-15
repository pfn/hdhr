package com.hanhuy.hdhr.stream;

import java.io.File;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class TimeShiftRingBuffer implements Runnable {
    private long offset = -1;   // physical position, position % size
    private long read_offset = -1;
    private final long size;
    private final FileChannel c;
    private boolean wrapped;

    private long wtotal, rtotal;
    private long nowpcr, basepcr;

    private ByteBuffer readBuffer;
    private ByteBuffer writeBuffer;

    private volatile boolean closed;

    private final static int _TMP_BUFFER_SIZE = 64 * 1024 * 1024;

    public final static long SEEK_NOW = -1;

    private final TimeShiftStream ts;

    private ArrayList<ByteBuffer> unmapQueue = new ArrayList<ByteBuffer>();

    public final static int BUFFER_SIZE = _TMP_BUFFER_SIZE -
            (_TMP_BUFFER_SIZE % RTPProxy.VIDEO_DATA_PACKET_SIZE);


    public TimeShiftRingBuffer(TimeShiftStream ts, File buffer, long size)
    throws IOException {
        // make the file a multiple of packet len
        size -= size % RTPProxy.VIDEO_DATA_PACKET_SIZE;

        this.size = size;
        RandomAccessFile raf = new RandomAccessFile(buffer, "rw");
        if (raf.length() < size) { // expand buffer to size
            raf.setLength(size);
        }
        c = raf.getChannel();
        this.ts = ts;
        Thread t = new Thread(this, "TimeShiftRingBuffer.unmap");
        t.start();
    }

    public void add(byte[] packet) {
        if (writeBuffer == null ||
                writeBuffer.remaining() < RTPProxy.VIDEO_DATA_PACKET_SIZE) {
            if (size - (offset + BUFFER_SIZE) < BUFFER_SIZE) {
                offset = -1;
                wrapped = true;
            }
            offset = offset == -1 ? 0 : offset + BUFFER_SIZE;
            unmap(writeBuffer);
            writeBuffer = null;
            mapWriteBuffer();
        }
        if (wrapped) {
            int l = writeBuffer.limit();
            int p = writeBuffer.position();
            writeBuffer.limit(p + RTPProxy.VIDEO_DATA_PACKET_SIZE);

            long pcr = ts.processPacket(writeBuffer.slice(), false, false);
            if (pcr != -1)
                basepcr = pcr;

            writeBuffer.limit(l);
            writeBuffer.position(p);
        }
        wtotal += RTPProxy.VIDEO_DATA_PACKET_SIZE;

        writeBuffer.put(packet);
    }

    public void nextPacket(byte[] packet) {
        if (readBuffer == null ||
                readBuffer.remaining() < RTPProxy.VIDEO_DATA_PACKET_SIZE) {
            if (size - (read_offset + BUFFER_SIZE) < BUFFER_SIZE) {
                read_offset = -1;
            }
            read_offset = read_offset == -1 ? 0 : read_offset + BUFFER_SIZE;
            unmap(readBuffer);
            readBuffer = null;

            if (rtotal > wtotal)
                throw new IllegalStateException("underflow");

            mapReadBuffer();
        }
        rtotal += RTPProxy.VIDEO_DATA_PACKET_SIZE;

        readBuffer.get(packet);
    }

    private void mapWriteBuffer() {
        while (writeBuffer == null && !closed) {
            try {
                if (offset == -1)
                    offset = 0;
                writeBuffer = c.map(
                        FileChannel.MapMode.READ_WRITE,
                        offset, BUFFER_SIZE);
                System.out.printf(
                        "Mapped new write buffer at 0x%08x PCR: %.3f\n",
                        offset, (double) nowpcr / 27000000);
            }
            catch (IOException e) {
                System.out.println("Unable to map write buffer, retrying");
                e.printStackTrace();
            }
        }
    }

    private void mapReadBuffer() {
        while (readBuffer == null && !closed) {
            try {
                if (read_offset == -1)
                    read_offset = 0;
                readBuffer = c.map(
                        FileChannel.MapMode.READ_ONLY,
                        read_offset, BUFFER_SIZE);
            }
            catch (IOException e) {
                System.out.println("Unable to map read buffer, retrying");
                e.printStackTrace();
            }
        }
    }

    public long seek(long pcr) {
        System.out.printf("SEEK: %.3f NOW: %.3f BASE: %.3f\n", (double) pcr / 27000000, (double) nowpcr / 27000000, (double) basepcr / 27000000);
        if (pcr > nowpcr)
            pcr = SEEK_NOW;

        if (pcr == SEEK_NOW) {
            System.out.println("SEEK: NOW");
            if (read_offset != offset) {
                read_offset = offset;
                unmap(readBuffer);
                readBuffer = null;
            }
            mapReadBuffer();
            mapWriteBuffer();
            readBuffer.position(writeBuffer.position());
            return SEEK_NOW;
        } else if (pcr <= basepcr + 27000) {
            System.out.println("SEEK: BASE");
            // won't be able to find basepcr if wrapped, since it's the last
            // pcr seen while being overwritten
            if (wrapped) {
                if (read_offset != offset) {
                    read_offset = offset;
                    unmap(readBuffer);
                    readBuffer = null;
                }
                mapReadBuffer();
                // give time to avoid overlapping read and write
                readBuffer.position(writeBuffer.position() +
                        30 * RTPProxy.VIDEO_DATA_PACKET_SIZE);
            } else {
                if (read_offset != 0) {
                    read_offset = 0;
                    unmap(readBuffer);
                    readBuffer = null;
                }
                mapReadBuffer();
                readBuffer.position(0);
            }
            pcr = basepcr + 27000;
        } else {
            long pos = pcr - basepcr;
            long range = nowpcr - basepcr;
            double r = (double) pos / range;

            long roff = (long) ((writeBuffer.position() + offset) * r);

            if (wrapped)
                roff = ((long) (size * r) + offset + writeBuffer.position()) % size;

            roff -= roff % BUFFER_SIZE;

            System.out.printf("Estimating TS at 0x%x, currently at 0x%x\n", roff, read_offset);
            if (read_offset != roff) {
                read_offset = roff;
                unmap(readBuffer);
                readBuffer = null;
                mapReadBuffer();
            } else {
                readBuffer.position(0);
            }
            int count = 0;
            long desiredpcr = pcr;
            do {
                count++;
                pcr = findPCR(readBuffer, desiredpcr, count < 2);
                if (pcr == -1) { // seek forward
                    System.out.println("Seeking forward");
                    read_offset += BUFFER_SIZE;
                    unmap(readBuffer);
                    readBuffer = null;
                    mapReadBuffer();
                } else if (pcr == -2) { // seek backward
                    System.out.println("Seeking backward");
                    read_offset -= BUFFER_SIZE;
                    unmap(readBuffer);
                    readBuffer = null;
                    mapReadBuffer();
                }

            } while (pcr < 0);
        }
        System.out.printf("SEEK: %.3f\n", (double) pcr / 27000000);
        return pcr;
    }

    private long findPCR(ByteBuffer b, long pcr, boolean willseek) {

        long lpcr = -1;
        int l = b.limit();
        boolean pcrfound = false;
        while (b.remaining() >
                RTPProxy.VIDEO_DATA_PACKET_SIZE) {

            int rpos = b.position();
            b.limit(rpos + RTPProxy.VIDEO_DATA_PACKET_SIZE);

            long p = ts.processPacket(b.slice(), false, false);
            try {
                if (p != -1) {
                    if (lpcr == -1) {
                        if (p <= pcr) {
                            lpcr = p;
                        } else {
                            // our estimate is off, the first pcr found is
                            // higher than what we're looking for
                            // seek backwards
                            System.out.printf("Desired time not found, should seek backward p > pcr: %.3f > %.3f\n", (double) p / 27000000, (double) pcr / 27000000);
                            lpcr = p;
                            if (willseek)
                                return -2;
                            break;
                        }
                    }
                    if (p > pcr && lpcr < pcr) {
                        lpcr = p;
                        pcr = lpcr;
                        pcrfound = true;
                        break;
                    }
                    if (p < pcr) {
                        lpcr = p;
                    }
                }
            }
            finally {
                b.limit(l);
                b.position(rpos +
                        RTPProxy.VIDEO_DATA_PACKET_SIZE);
            }
        }
        if (!pcrfound) {
            System.out.println(
                    "Desired time not found, should seek forward");
            if (willseek)
                return -1;
        }
        pcr = lpcr;
        return pcr;
    }

    public void close() {
        synchronized (this) {
            closed = true;
            notify();
        }
        try {
            c.close();
        }
        catch (IOException e) { }
        unmap(readBuffer);
        unmap(writeBuffer);
    }

    public void setStartPCR(long pcr) {
        basepcr = pcr;
    }
    public long getStartPCR() {
        return basepcr;
    }
    public void setCurrentPCR(long pcr) {
        nowpcr = pcr;
    }

    private void unmap(ByteBuffer buffer) {
        if (buffer == null) return;
        synchronized (unmapQueue) {
            unmapQueue.add(buffer);
        }
    }

    /**
     * Oh!  This is so bad...  MappedByteBuffer has no way to unmap, do this.
     */
    private void _unmap(ByteBuffer buffer) {
        if (buffer == null) return;
        try {
            java.lang.reflect.Method m = buffer.getClass().getMethod("cleaner");
            m.setAccessible(true);
            Object c = m.invoke(buffer);
            // avoid warning on sun.misc.Cleaner
            java.lang.reflect.Method clean = c.getClass().getMethod("clean");
            clean.setAccessible(true);
            clean.invoke(c);
        }
        catch (Exception e) {
            if (e instanceof RuntimeException)
                throw (RuntimeException) e;
            throw new IllegalStateException(e);
        }
    }

    public synchronized void run() {
        while (!closed) {

            ArrayList<ByteBuffer> queue = null;
            synchronized (unmapQueue) {
                if (unmapQueue.size() > 0) {
                    queue = new ArrayList<ByteBuffer>(unmapQueue);
                    unmapQueue.clear();
                }
            }

            if (queue != null) {
                for (ByteBuffer b : queue)
                    _unmap(b);
            }
            try {
                wait(60 * 1000);
            }
            catch (InterruptedException e) { }
        }
    }
}
