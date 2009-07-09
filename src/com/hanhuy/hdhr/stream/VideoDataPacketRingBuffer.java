package com.hanhuy.hdhr.stream;

import java.nio.ByteBuffer;

public class VideoDataPacketRingBuffer {
    private final ByteBuffer buffer;
    private final int size;

    private long wtotal;
    private long rtotal;
    private int count;
    private int wpos;
    private int rpos;

    public VideoDataPacketRingBuffer(int size) {
        size -= size % RTPProxy.VIDEO_DATA_PACKET_SIZE;

        buffer = ByteBuffer.allocateDirect(size);
        this.size = buffer.capacity() -
                (buffer.capacity() % RTPProxy.VIDEO_DATA_PACKET_SIZE);
    }

    public int size() {
        return size;
    }

    public void clear() {
        wtotal = rtotal = count = wpos = rpos = 0;
    }

    public int available() {
        return count;
    }

    public void add(byte[] packet) {
        int len = packet.length;
        wtotal += len;

        if (wtotal - rtotal > size)
            throw new IllegalStateException("buffer overflow");

        buffer.position(wpos);
        if (buffer.remaining() < len) {
            buffer.position(0);
        }

        buffer.put(packet);
        wpos = buffer.position();
        count++;
    }

    public void nextPacket(byte[] packet) {
        if (count < 1)
            throw new IllegalStateException("buffer underflow");

        int len = packet.length;
        rtotal += len;

        buffer.position(rpos);
        if (buffer.remaining() < len) {
            buffer.position(0);
        }

        buffer.get(packet);

        rpos = buffer.position();

        count--;
    }
}
