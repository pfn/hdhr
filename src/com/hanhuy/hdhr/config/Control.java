package com.hanhuy.hdhr.config;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Random; // don't need secure

public class Control {

    public enum Tuner { TUNER0, TUNER1 }

    public final static Set<String> TUNER_COMMANDS;
    static {
        HashSet<String> commands = new HashSet<String>(Arrays.asList(
            "channel",
            "channelmap",
            "filter",
            "program",
            "target",
            "status",
            "streaminfo",
            "debug",
            "lockkey"
        ));
        TUNER_COMMANDS = Collections.unmodifiableSet(commands);
    }

    private SocketChannel c;
    private int lockkey = 0;
    private Random random = new Random();
    private Tuner tuner;
    private int connectedId;

    public Tuner getTuner() throws TunerUnavailableException {
        if (tuner == null)
            throw new TunerUnavailableException("No tuner locked");
        return tuner;
    }
    public String get(String name) throws TunerException {
        return set(name, null);
    }

    public boolean isLocked() throws TunerException {
        if (tuner != null) {
            String key = get("lockkey");
            if ("none".equals(key)) {
                tuner = null;     // not locked, try locking
            } else {
                int k = (int) Long.parseLong(key);
                if (lockkey == k) // already locked
                    return true;
                lockkey = 0;      // not locked, try locking
            }
        }
        return false;
    }
    public void lock(Tuner t) throws TunerException, TunerUnavailableException {
        if (tuner != null && tuner != t) {
            throw new TunerUnavailableException(
                    "only control 1 tuner per Control instance; unlock first");
        }
        if (isLocked())
            return;

        // Math.abs is a silly workaround for java's lack of %u format
        int key = Math.abs(random.nextInt());
        set(String.format("/tuner%d/lockkey", t.ordinal()),
                Integer.toString(key));
        tuner = t;
        lockkey = key;
    }
    public void lock() throws TunerException, TunerUnavailableException {
        if (isLocked())
            return;

        int key;
        for (Tuner t : Tuner.values()) {
            try {
                lock(t);
                break;
            }
            catch (TunerErrorException e) { } // probably a locked resource
        }
        if (tuner == null)
            throw new TunerUnavailableException("Unable to lock a tuner");
    }

    public void unlock() throws TunerException {
        if (tuner == null)
            return;
        set(String.format("/tuner%d/lockkey", tuner.ordinal()), "none");
        lockkey = 0;
        tuner   = null;
    }

    public void force(Tuner t) throws TunerException {
        lockkey = 0;
        set(String.format("/tuner%d/lockkey", t.ordinal()), "force");
        if (tuner == t)
            tuner = null;
    }

    public String set(String name, String value)
    throws TunerException, TunerErrorException {
        if (c == null)
            throw new TunerException("not connected");

        try {
            if (tuner != null && TUNER_COMMANDS.contains(name))
                name = String.format("/tuner%d/%s", tuner.ordinal(), name);

            Packet p = new Packet();
            p.addTag(Packet.Tag.GETSET_NAME, name);

            if (value != null)
                p.addTag(Packet.Tag.GETSET_VALUE, value);

            if (lockkey != 0)
                p.addTag(Packet.Tag.GETSET_LOCKKEY, lockkey);

            ByteBuffer b = p.seal(Packet.Type.GETSET_REQ);

            c.write(b);
            c.read(p.buffer());
            p.parse();
            if (p.getType() != Packet.Type.GETSET_RPY)
                throw new TunerException("Unexpected packet: " + p.getType());

            if (p.getTagMap().containsKey(Packet.Tag.ERROR_MESSAGE)) {
                throw new TunerErrorException(Packet.decodeString(
                        p.getTagMap().get(Packet.Tag.ERROR_MESSAGE)));
            }
            String rName = Packet.decodeString(
                    p.getTagMap().get(Packet.Tag.GETSET_NAME));
            if (!name.equals(rName)) {
                throw new TunerException(String.format(
                        "get/set name mismatch: %s != %s", name, rName));
            }
            String rValue = Packet.decodeString(
                    p.getTagMap().get(Packet.Tag.GETSET_VALUE));
            return rValue;
        }
        catch (SocketException e) {
            throw new TunerException(e.getMessage(), e);
        }
        catch (IOException e) {
            throw new TunerException(e.getMessage(), e);
        }
    }

    /**
     * Connect to the specified deviceId.  If deviceId is
     * Packet.DEVICE_ID_WILDCARD then connect to the first device returned.
     */
    public int connect(int deviceId) throws TunerException {
        if (c != null)
            return connectedId;
        connectedId = deviceId;
        try {
            Map<Integer, InetAddress[]> devices = Discover.discover(deviceId);
            InetAddress[] endpoints;
            if (devices.size() == 0) {
                throw new TunerException("Device not found: " +
                        Integer.toHexString(deviceId));
            }
            if (deviceId == Packet.DEVICE_ID_WILDCARD) {
                Iterator<Map.Entry<Integer,InetAddress[]>> i =
                        devices.entrySet().iterator();
                if (i.hasNext()) {
                    Map.Entry<Integer,InetAddress[]> e = i.next();
                    connectedId = e.getKey();
                    endpoints = e.getValue();
                } else
                    throw new RuntimeException("No device found");
            } else {
                endpoints = devices.get(deviceId);
            }

            c = SocketChannel.open();
            c.socket().setSoTimeout(5000);
            c.socket().bind(new InetSocketAddress(endpoints[0], 0));
            c.connect(new InetSocketAddress(
                    endpoints[1], Packet.CONTROL_TCP_PORT));
        }
        catch (SocketException e) {
            throw new TunerException(e.getMessage(), e);
        }
        catch (IOException e) {
            throw new TunerException(e.getMessage(), e);
        }
        return connectedId;
    }

    public void close() {
        if (c == null) return;
        try {
            c.close();
        }
        catch (IOException e) { } // ignore
        c = null;
    }

    /**
     * Utility method for splitting a status string out
     * @return a Map with keys ch, lock, ss, snq, seq, bps and pps
     */
    public static Map<String,String> parseStatus(String status) {
        Map<String,String> m = new HashMap<String,String>();
        String[] elem = status.split(" ");
        for (String s : elem) {
            String[] vals = s.split("=");
            m.put(vals[0], vals[1]);
        }
        return m;
    }

    public static void main(String[] args) throws Exception {
        Control c = new Control();
        c.connect(Integer.parseInt("10165722", 16));
        c.lock();
        System.out.println(c.get("debug"));
        c.unlock();
        c.close();
    }
}
