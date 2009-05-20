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
        buf = ByteBuffer.allocate(3074);
        reset();
    }

    public ByteBuffer buffer() {
        reset();
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
        buf.position(1024);
    }

    private void reset() {
        buf.position(1024);
        buf.limit(buf.capacity() - 4);
        sealed = false;
        type = null;
        tagMap = null;
    }

    public void parse() {
        flip();
        buf.limit(buf.limit() - 4);
        int crc = crc();
        buf.limit(buf.limit() + 4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        int packetCrc = buf.getInt();
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.position(buf.position() - 4);
        flip();

        short pType = buf.getShort();
        short pLen  = buf.getShort(); // unused
        for (Type t : Type.values()) {
            if (t.value == pType) {
                type = t;
                break;
            }
        }
        if (type == null)
            throw new IllegalStateException("Unknown packet type: " + pType);

        if (crc != packetCrc) {
            throw new IllegalStateException("CRC32 error: " +
                    Integer.toHexString(crc) + " != " +
                    Integer.toHexString(packetCrc));
        }
        sealed = true;
    }

    public Type getType() {
        if (!sealed)
            throw new IllegalStateException("Packet type unknown");
        return type;
    }

    public byte[] seal(Type type) {
        if (sealed) throw new IllegalStateException("Packet has been sealed");
        this.type = type;
        flip();
        short len = (short) buf.remaining();

        // frame type and length here
        buf.position(buf.position() - 4);
        buf.putShort(type.value);
        buf.putShort(len);
        buf.position(buf.position() - 4);

        // crc here (includes type and length)
        int crc = crc();
        buf.position(buf.limit());
        buf.limit(buf.limit() + 4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(crc);
        buf.order(ByteOrder.BIG_ENDIAN);

        flip();
        buf.position(buf.position() - 4);
        byte[] b = new byte[buf.remaining()];
        buf.get(b);
        sealed = true;
        return b;
    }

    private int crc() {
        byte[] b = new byte[buf.remaining()];
        buf.get(b);

        CRC32 c = new CRC32();
        c.update(b);
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
                throw new IllegalStateException("Unknown tag: " + tag);
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
     * The packet may only be iterated once.
     */
    public Iterator<TagEntry> tagIterator() {
        if (!sealed)
            throw new IllegalStateException("cannot iterate unsealed packet");

        return new Iterator<TagEntry>() {
            private TagEntry entry;
            public boolean hasNext() {
                if (buf.remaining() > 2) {
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
                if (current == null)
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
