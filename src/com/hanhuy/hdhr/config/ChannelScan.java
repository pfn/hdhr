package com.hanhuy.hdhr.config;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.EventListener;
import java.util.EventObject;

public class ChannelScan {

    private final static Pattern PROGRAM_PATTERN =
            Pattern.compile("(\\d+): (\\d+)(\\.\\d+)?\\s?(.+)?");

    public static List<ChannelMap.Channel> scan(
            Control connection, ScanListener l)
    throws TunerException, TunerUnavailableException  {
        List<ChannelMap.Channel> availableChannels =
                new ArrayList<ChannelMap.Channel>();
        String mapName = connection.get("channelmap");
        ChannelMap map = ChannelMap.forName(mapName);
        List<ChannelMap.Channel> channels = map.getChannels();

        int index = 0;
        for (ChannelMap.Channel c : channels) {
            index++;

            connection.set("channel", "auto:" + c.frequency);
            sleep(250);

            l.scanningChannel(new ScanEvent(c, map, index));
            if (!waitForSignal(connection))
                continue;

            waitForSeq(connection); // timeout ok

            getStreamInfo(connection, c);
            if (c.getPrograms().size() > 0) {
                availableChannels.add(c);
                l.programsFound(new ScanEvent(c, map, index));
            }
        }
        return availableChannels;
    }

    static void getStreamInfo(Control connection, ChannelMap.Channel c)
    throws TunerException {
        long timeout = System.currentTimeMillis() + 10000;
        long change_timeo = System.currentTimeMillis() + 2000;
        boolean changed = false;
        boolean incomplete;

        List<ChannelMap.Channel.Program> programs =
            new ArrayList<ChannelMap.Channel.Program>();
        int program_count = 0;
        do {
            int encrypted = 0;
            programs.clear();
            incomplete = false;
            String streaminfo = connection.get("streaminfo");
            String[] info = streaminfo.split("\\n");

            if (changed) {
                change_timeo = System.currentTimeMillis() + 2000;
            }
            sleep(250);

            for (String line : info) {
                Matcher m = PROGRAM_PATTERN.matcher(line);

                if (line.startsWith("crc=0x")) {
                    String crcStr = line.substring(line.indexOf("x") + 1);
                    int crc = (int) Long.parseLong(crcStr, 16);
                    c.setPatCRC(crc);
                    continue;
                }
                if (line.contains("(no data)")) {
                    incomplete = true;
                    break;
                }
                if (line.contains("(encrypted)")) {
                    encrypted++;
                    continue;
                }

                if (!m.matches())
                    continue;
                String numberS = m.group(1);
                String majorS = m.group(2);
                String minorS = m.group(3);
                short number = Short.parseShort(numberS);
                short major = Short.parseShort(majorS);
                short minor = 0;
                if (minorS != null)
                    minor = Short.parseShort(minorS.substring(1)); // skip .
                String name = m.group(4);

                ChannelMap.Channel.Program p =
                        new ChannelMap.Channel.Program(c, number, major, minor);
                p.setName(name);
                programs.add(p);
            }
            changed = encrypted + programs.size() != program_count;
            program_count = programs.size() + encrypted;
        } while (System.currentTimeMillis() < timeout &&
                (!incomplete && System.currentTimeMillis() < change_timeo));
        c.getPrograms().addAll(programs);
    }
    static boolean waitForSignal(Control connection) throws TunerException {
        long timeo = System.currentTimeMillis() + 2500;
        int ss = 0;
        String lock = null;
        do {
            String status = connection.get("status");
            Map<String,String> stat = parseStatus(status);
            String ssStr = stat.get("ss");
            ss = Integer.parseInt(ssStr);
            lock = stat.get("lock");
            sleep(250);
        } while (System.currentTimeMillis() < timeo && ss > 45
                && "none".equals(lock));

        return !"none".equals(lock) && !lock.startsWith("(");
    }

    // wait for seq-lock or just timeout, no ill effect
    static void waitForSeq(Control connection) throws TunerException {
        long timeo = System.currentTimeMillis() + 5000;
        int seq = 0;
        do {
            String status = connection.get("status");
            Map<String,String> stat = parseStatus(status);
            String seqStr = stat.get("seq");
            seq = Integer.parseInt(seqStr);
            sleep(250);
        }  while (System.currentTimeMillis() < timeo && seq != 100);
    }

    static Map<String,String> parseStatus(String status) {
        Map<String,String> m = new HashMap<String,String>();
        String[] elem = status.split(" ");
        for (String s : elem) {
            String[] vals = s.split("=");
            m.put(vals[0], vals[1]);
        }
        return m;
    }

    private static void sleep(int time) {
        try { Thread.sleep(time); }
        catch (InterruptedException e) { } // ignore
    }
    public static void main(String[] args) throws Exception {
        Control c = new Control();
        c.connect(Packet.DEVICE_ID_WILDCARD);
        c.lock();
        System.out.println("Locked tuner: " + c.getTuner());
        scan(c, new ScanListener() {
            int programs = 0;
            public void scanningChannel(ScanEvent e) {
                System.out.println(String.format(
                    "Scanning %d/%d: %s",
                    e.index, e.map.getChannels().size(), e.channel
                ));
            }
            public void programsFound(ScanEvent e) {
                programs += e.channel.getPrograms().size();
                for (ChannelMap.Channel.Program p : e.channel.getPrograms())
                    System.out.println(p);
                System.out.println("Total programs so far: " + programs);
            }
        });
        c.unlock();
        c.close();
    }

    public static class ScanEvent extends EventObject {
        public final ChannelMap.Channel channel;
        public final ChannelMap map;
        /**
         * 1-based
         */
        public final int index;
        public ScanEvent(ChannelMap.Channel c, ChannelMap m, int i) {
            super(c);
            channel = c;
            map = m;
            index = i;
        }
    }
    public interface ScanListener extends EventListener {
        public void scanningChannel(ScanEvent e);
        public void programsFound(ScanEvent e);
    }
}
