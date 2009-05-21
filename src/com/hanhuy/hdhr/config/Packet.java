package com.hanhuy.hdhr.config;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.zip.CRC32;

public class Packet {

    public final static int   DEVICE_ID_WILDCARD = 0xffffffff;
    public final static int   DEVICE_TYPE_TUNER  = 0x00000001;

    public final static int   DISCOVER_UDP_PORT  = 65001;
    public final static int   CONTROL_TCP_PORT   = 65001;

    // leaves 2050 bytes for actual data
    private final static int BUFFER_SIZE  = 2058;
    private final static int BUFFER_START = 4; // type and size
    private final static int BUFFER_CRC   = 4; // 4 byte crc

    public enum Tag {
        DEVICE_TYPE    ((byte)0x01),
        DEVICE_ID      ((byte)0x02),
        GETSET_NAME    ((byte)0x03),
        GETSET_VALUE   ((byte)0x04),
        GETSET_LOCKKEY ((byte)0x15),
        ERROR_MESSAGE  ((byte)0x05);
        final byte value;
        Tag(byte b) {
            value = b;
        }
    }

    public enum Type {
        DISCOVER_REQ ((short)0x0002),
        DISCOVER_RPY ((short)0x0003),
        GETSET_REQ   ((short)0x0004),
        GETSET_RPY   ((short)0x0005),
        UPGRADE_REQ  ((short)0x0002),
        UPGRADE_RPY  ((short)0x0003);
        final short value;
        Type(short s) {
            value = s;
        }
    }

    private static final Charset cs = Charset.forName("UTF-8");
    private final ByteBuffer buf;
    private Map<Tag,byte[]> tagMap;
    private boolean sealed = false;
    private Type type;

    public Packet() {
        buf = ByteBuffer.allocate(BUFFER_SIZE);
        reset();
    }

    /**
     * @return a buffer meant to be passed to ReadableByteChannel.read()
     */
    public ByteBuffer buffer() {
        reset();
        buf.clear();
        return buf;
    }

    public void addTag(Tag tag, int value) {
        buf.put(tag.value);
        buf.put(encodeVarlen(4));
        buf.putInt(value);
    }

    public void addTag(Tag tag, String value) {
        buf.put(tag.value);
        buf.put(encodeVarlen(value.length() + 1));
        buf.put(cs.encode(value));
        buf.put((byte) 0);
    }

    private void flip() {
        buf.limit(buf.position());
        buf.position(BUFFER_START);
    }

    private void reset() {
        buf.position(BUFFER_START);
        buf.limit(buf.capacity() - BUFFER_CRC);
        sealed = false;
        type = null;
        tagMap = null;
    }

    public void parse() {
        flip();
        buf.position(buf.position() - BUFFER_START);
        buf.limit(buf.limit() - BUFFER_CRC);

        int crc = crc();

        buf.position(buf.limit());
        buf.limit(buf.limit() + BUFFER_CRC);

        buf.order(ByteOrder.LITTLE_ENDIAN);
        int packetCrc = buf.getInt();
        buf.order(ByteOrder.BIG_ENDIAN);

        if (crc != packetCrc) {
            throw new IllegalStateException("CRC32 error: " +
                    Integer.toHexString(crc) + " != " +
                    Integer.toHexString(packetCrc));
        }

        flip();
        buf.limit(buf.limit() - BUFFER_CRC);
        buf.position(buf.position() - BUFFER_START);

        short pType = buf.getShort();
        buf.getShort(); // unused, packet length
        for (Type t : Type.values()) {
            if (t.value == pType) {
                type = t;
                break;
            }
        }

        if (type == null)
            throw new IllegalStateException("Unknown packet type: 0x" +
                    Integer.toHexString(pType));

        sealed = true;
    }

    public Type getType() {
        if (!sealed)
            throw new IllegalStateException("Packet type unknown");
        return type;
    }

    public ByteBuffer seal(Type type) {
        if (sealed) throw new IllegalStateException("Packet has been sealed");
        this.type = type;
        flip();
        short len = (short) buf.remaining();

        // frame type and length here
        buf.position(buf.position() - BUFFER_START);
        buf.putShort(type.value);
        buf.putShort(len);
        buf.position(buf.position() - BUFFER_START);

        // crc here (includes type and length)
        int crc = crc();

        buf.position(buf.limit());
        buf.limit(buf.limit() + BUFFER_CRC);

        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(crc);
        buf.order(ByteOrder.BIG_ENDIAN);

        flip();
        buf.position(buf.position() - BUFFER_START);
        sealed = true;
        return buf.slice().asReadOnlyBuffer();
    }

    private int crc() {
        CRC32 c = new CRC32();
        c.update(buf.array(), buf.arrayOffset() + buf.position(),
                buf.remaining());
        return (int) c.getValue();
    }

    private static byte[] encodeVarlen(int i) {
        if (i <= 127) {
            return new byte[] { (byte) i };
        }
        byte[] b = new byte[2];
        b[0] = (byte) (i | 0x80);
        b[1] = (byte) (i >> 7);
        return b;
    }

    private short decodeVarlen() {
        if (buf.remaining() < 1)
            throw new IllegalStateException("not enough data");
        short len = buf.get();
        if ((len & 0x0080) != 0) {
            if (buf.remaining() < 1)
                throw new IllegalStateException("not enough data");
            len &= 0x007f;
            len |= (buf.get() << 7);
        }
        return len;
    }

    /**
     * Utility to decode a byte-array to a string, strips NUL if any.
     */
    public static String decodeString(byte[] value) {
        ByteBuffer b = ByteBuffer.wrap(value);
        if (value[value.length - 1] == 0)
            b.limit(b.limit() - 1);
        return cs.decode(b).toString();
    }

    public static class TagEntry {
        public final Tag tag;
        public final byte[] value;
        TagEntry(byte tag, byte[] value) {
            Tag temp = null;
            for (Tag t : Tag.values()) {
                if (t.value == tag) {
                    temp = t;
                    break;
                }
            }
            this.tag = temp;
            if (this.tag == null) {
                throw new IllegalStateException("Unknown tag: 0x" +
                        Integer.toHexString(tag));
            }
            this.value = value;
        }
    }

    /**
     * Uses tagIterator to generate a Map, cannot use tagIterator after
     * obtaining this map (unless the packet is reset and recreated)
     */
    public Map<Tag,byte[]> getTagMap() {
        if (tagMap != null)
            return tagMap;

        tagMap = new HashMap<Tag,byte[]>();

        Iterator<TagEntry> i = tagIterator();
        while (i.hasNext()) {
            TagEntry e = i.next();
            tagMap.put(e.tag, e.value);
        }
        return tagMap;
    }

    /**
     * The packet may only be iterated once.  flip() can enable the packet to
     * be iterated again.
     */
    public Iterator<TagEntry> tagIterator() {
        if (!sealed)
            throw new IllegalStateException("cannot iterate unsealed packet");

        return new Iterator<TagEntry>() {
            private TagEntry entry;
            public boolean hasNext() {
                if (buf.remaining() > 2 && entry == null) {
                    byte tag = buf.get();
                    short len = decodeVarlen();
                    byte[] data = new byte[len];
                    buf.get(data);

                    entry = new TagEntry(tag, data);
                }
                return entry != null;
            }
            public TagEntry next() {
                TagEntry current = entry;
                if (!hasNext())
                    throw new NoSuchElementException("No more elements");
                entry = null;
                return current;
            }
            public void remove() {
                throw new UnsupportedOperationException("remove");
            }
        };
    }
}
