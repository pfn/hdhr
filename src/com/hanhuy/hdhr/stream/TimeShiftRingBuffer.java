package com.hanhuy.hdhr.stream;

import java.io.File;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;

public class TimeShiftRingBuffer {
    private long offset = -1;   // physical position, position % size
    private long read_offset = -1;
    private final long size;
    private final FileChannel c;
    private boolean wrapped;

    private long wtotal, rtotal;
    private long nowpcr, basepcr;

    private ByteBuffer readBuffer;
    private ByteBuffer writeBuffer;

    private final static int _TMP_BUFFER_SIZE = 64 * 1024 * 1024;

    public final static long SEEK_NOW = -1;

    private final TimeShiftStream ts;

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

            long pcr = ts.processPacket(writeBuffer, false, false);
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
        while (writeBuffer == null) {
            try {
                if (offset == -1)
                    offset = 0;
                writeBuffer = c.map(
                        FileChannel.MapMode.READ_WRITE,
                        offset, BUFFER_SIZE);
            }
            catch (IOException e) {
                System.out.println("Unable to map write buffer, retrying: " +
                        e.getMessage());
            }
        }
    }

    private void mapReadBuffer() {
        while (readBuffer == null) {
            try {
                if (read_offset == -1)
                    read_offset = 0;
                readBuffer = c.map(
                        FileChannel.MapMode.READ_ONLY,
                        read_offset, BUFFER_SIZE);
            }
            catch (IOException e) {
                System.out.println("Unable to map read buffer, retrying: " +
                        e.getMessage());
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

            long lpcr = -1;
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

            int l = readBuffer.limit();
            while (readBuffer.remaining() >
                    RTPProxy.VIDEO_DATA_PACKET_SIZE) {

                int rpos = readBuffer.position();
                readBuffer.limit(rpos + RTPProxy.VIDEO_DATA_PACKET_SIZE);

                long p = ts.processPacket(readBuffer.slice(), false, false);
                try {
                    if (p != -1) {
                        if (lpcr == -1) {
                            if (p <= pcr) {
                                lpcr = p;
                            } else {
                                // our estimate is off, the first pcr found is
                                // higher than what we're looking for
                                System.out.printf("unsupported p > pcr: %.3f > %.3f\n", (double) p / 27000000, (double) pcr / 27000000);
                                pcr = p;
                                break;
                            }
                        }
                        if (p > pcr && lpcr < pcr) {
                            lpcr = p;
                            pcr = lpcr;
                            break;
                        }
                        if (p < pcr) {
                            lpcr = p;
                        }
                    }
                }
                finally {
                    readBuffer.limit(l);
                    readBuffer.position(rpos +
                            RTPProxy.VIDEO_DATA_PACKET_SIZE);
                }
            }
            pcr = lpcr;
        }
        System.out.printf("SEEK: %.3f\n", (double) pcr / 27000000);
        return pcr;
    }

    public void close() {
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
    public void setCurrentPCR(long pcr) {
        nowpcr = pcr;
    }

    /**
     * Oh!  This is so bad...  MappedByteBuffer has no way to unmap, do this.
     */
    private void unmap(ByteBuffer buffer) {
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
}
