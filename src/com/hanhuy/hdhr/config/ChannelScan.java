package com.hanhuy.hdhr.config;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.EventListener;
import java.util.EventObject;

public class ChannelScan {

    private final static Pattern PROGRAM_PATTERN =
            Pattern.compile("(\\d+): (\\d+)(\\.\\d+)?\\s?(.+)?");

    /**
     * 5.5mhz impossible channel window
     */
    public final static int LOCK_SKIP = 5500000;

    public static List<ChannelMap.Channel> scan(
            Control connection, ScanListener l)
    throws TunerException, TunerUnavailableException  {
        List<ChannelMap.Channel> availableChannels =
                new ArrayList<ChannelMap.Channel>();
        String mapName = connection.get("channelmap");
        ChannelMap map = ChannelMap.forName(mapName);
        List<ChannelMap.Channel> channels = map.getChannels();

        int index = 0;
        int nextFrequency = 0;
        for (ChannelMap.Channel c : channels) {
            index++;

            ScanEvent e = new ScanEvent(c, map, index);
            l.scanningChannel(new ScanEvent(c, map, index));
            if (e.cancelled)
                break;
            if (nextFrequency > c.frequency) {
                // don't scan, a channel already occupies this space
                l.skippedChannel(e);
                if (e.cancelled)
                    break;
                continue;
            }
            connection.set("channel", "auto:" + c.frequency);
            sleep(250);

            Map<String,String> status = null;
            String lock;
            try {
                lock = waitForSignal(connection);
            }
            catch (ChannelSkippedException ex) {
                e = new ScanEvent(c, map, index, ex.status);
                l.skippedChannel(e);
                if (e.cancelled)
                    break;
                continue;
            }

            c.setModulation(lock);
            status = waitForSeq(connection); // timeout ok
            e = new ScanEvent(c, map, index, status);
            l.foundChannel(e);
            if (e.cancelled)
                break;

            String streaminfo = getStreamInfo(connection, c);
            e = new ScanEvent(c, map, index, streaminfo);
            if (c.getPrograms().size() > 0) {
                availableChannels.add(c);
                l.programsFound(e);
            } else {
                l.programsNotFound(e);
            }
            if (e.cancelled)
                break;

            nextFrequency = c.frequency + LOCK_SKIP;
        }
        connection.set("channel", "none");
        return availableChannels;
    }

    static String getStreamInfo(Control connection, ChannelMap.Channel c)
    throws TunerException {
        String model = connection.get("/sys/model");
        String streaminfo;
        long timeout;
        long change_timeo;
        if (model.contains("atsc")) {
            timeout = System.currentTimeMillis() + 4000;
            change_timeo = System.currentTimeMillis() + 1000;
        } else {
            timeout = System.currentTimeMillis() + 10000;
            change_timeo = System.currentTimeMillis() + 2000;
        }
        boolean changed = false;
        boolean incomplete;

        List<ChannelMap.Channel.Program> programs =
            new ArrayList<ChannelMap.Channel.Program>();
        int program_count = 0;
        do {
            int encrypted = 0;
            programs.clear();
            incomplete = false;
            streaminfo = connection.get("streaminfo");
            String[] info = streaminfo.split("\\n");

            sleep(250);
            if (changed) {
                change_timeo = System.currentTimeMillis() +
                       (model.contains("atsc") ? 1000 : 2000);
            }

            for (String line : info) {
                Matcher m = PROGRAM_PATTERN.matcher(line);

                if (line.startsWith("crc=0x")) {
                    String crcStr = line.substring(line.indexOf("x") + 1);
                    int crc = (int) Long.parseLong(crcStr, 16);
                    c.setPatCRC(crc);
                    continue;
                }
                if (line.startsWith("tsid=0x")) {
                    String crcStr = line.substring(line.indexOf("x") + 1);
                    int tsid = (int) Long.parseLong(crcStr, 16);
                    c.setTsID(tsid);
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
                (incomplete || System.currentTimeMillis() < change_timeo));
        c.getPrograms().addAll(programs);
        return streaminfo;
    }
    private static class ChannelSkippedException extends Exception {
        final Map<String,String> status;
        ChannelSkippedException(Map<String,String> status) {
            this.status = status;
        }
    }
    /**
     * @return modulation type (qam64, qam256, 8vsb, etc.), null if none 
     *         or an unsupported type (ntsc, etc.)
     * @throws ChannelSkippedException if an unsupported modulation type
     *         or no modulation type is found.
     */
    private static String waitForSignal(Control connection)
    throws TunerException, ChannelSkippedException {
        long timeo = System.currentTimeMillis() + 2500;
        int ss = 0;
        String lock = null;
        Map<String,String> stat;
        do {
            String status = connection.get("status");
            stat = Control.parseStatus(status);
            String ssStr = stat.get("ss");
            ss = Integer.parseInt(ssStr);
            lock = stat.get("lock");
            sleep(250);
        } while (System.currentTimeMillis() < timeo && ss > 45
                && "none".equals(lock));

        lock = !"none".equals(lock) && !lock.startsWith("(") ? lock : null;

        if (lock == null)
            throw new ChannelSkippedException(stat);

        return lock;
    }

    // wait for seq-lock or just timeout, no ill effect
    static Map<String,String> waitForSeq(Control connection)
    throws TunerException {
        long timeo = System.currentTimeMillis() + 5000;
        int seq = 0;
        Map<String,String> stat;
        do {
            String status = connection.get("status");
            stat = Control.parseStatus(status);
            String seqStr = stat.get("seq");
            seq = Integer.parseInt(seqStr);
            sleep(250);
        }  while (System.currentTimeMillis() < timeo && seq != 100);
        return stat;
    }

    private static void sleep(int time) {
        try { Thread.sleep(time); }
        catch (InterruptedException e) { } // ignore
    }
    public static void main(String[] args) throws Exception {
        Control c = new Control();
        int id = c.connect(Packet.DEVICE_ID_WILDCARD);
        c.lock();
        System.out.println("Connected to device " + Integer.toHexString(id));
        System.out.println("Locked tuner: " + c.getTuner());
        scan(c, new ScanListener() {
            int programs = 0;
            public void scanningChannel(ScanEvent e) {
                System.out.println(String.format(
                    "Scanning %d/%d: %s",
                    e.index, e.map.getChannels().size(), e.channel
                ));
            }
            public void skippedChannel(ScanEvent e) {
                System.out.println("Skipped: " +
                        (e.getStatus() != null ?
                                e.getStatus() : "impossible channel"));
            }
            public void foundChannel(ScanEvent e) {
                System.out.println("Locked: " + e.getStatus());
            }
            public void programsFound(ScanEvent e) {
                programs += e.channel.getPrograms().size();
                System.out.println("BEGIN STREAMINFO:\n" + e.streaminfo +
                        ":END STREAMINFO");
                for (ChannelMap.Channel.Program p : e.channel.getPrograms())
                    System.out.println(p);

                System.out.println("Total programs so far: " + programs);
            }
            public void programsNotFound(ScanEvent e) {
                System.out.println("BEGIN STREAMINFO:" + e.streaminfo +
                        ":END STREAMINFO");
                System.out.println("No available programs found");
            }
        });
        c.unlock();
        c.close();
    }

    public static class ScanEvent extends EventObject {
        public final ChannelMap.Channel channel;
        public final ChannelMap map;
        public final String streaminfo;
        private Map<String,String> status;
        private boolean cancelled = false;
        /**
         * 1-based
         */
        public final int index;
        public ScanEvent(ChannelMap.Channel c, ChannelMap m, int i,
                Map<String,String> status) {
            this(c, m, i, (String) null);
            this.status = status;
        }
        public ScanEvent(ChannelMap.Channel c, ChannelMap m, int i) {
            this(c, m, i, (String) null);
        }
        public ScanEvent(
                ChannelMap.Channel c, ChannelMap m, int i, String streaminfo) {
            super(c);
            channel = c;
            map = m;
            index = i;
            this.streaminfo = streaminfo;
        }

        public Map<String,String> getStatus() {
            return status;
        }

        public void cancelScan() {
            cancelled = true;
        }
    }
    /**
     * Order of events:
     *   scanningChannel,
     *       foundChannel or skippedChannel,
     *       if foundChannel:
     *           programsFound or programsNotFound
     */
    public interface ScanListener extends EventListener {
        public void scanningChannel(ScanEvent e);
        public void foundChannel(ScanEvent e);
        public void skippedChannel(ScanEvent e);
        public void programsFound(ScanEvent e);
        public void programsNotFound(ScanEvent e);
    }
}
