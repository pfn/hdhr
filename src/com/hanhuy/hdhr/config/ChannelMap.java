package com.hanhuy.hdhr.config;

import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;
import java.io.Serializable;

public class ChannelMap implements Serializable {

    private final static long serialVersionUID = 200905251503l;
    public final static int FREQUENCY_RESOLUTION = 62500;
    public final String name;
    private List<ChannelRange> ranges = new ArrayList<ChannelRange>();
    private List<String> countries = new ArrayList<String>();
    private static Map<String,ChannelMap> channelmaps;
    private List<ChannelMap> submaps = new ArrayList<ChannelMap>();
    private List<Channel> channels;
    private Map<Integer,Channel> channelMap;

    private ChannelMap(String name) { this.name = name; }

    static {
        channelmaps = new HashMap<String,ChannelMap>();
        ChannelMap map;

        map = new ChannelMap("us-bcast");
        map.ranges.addAll(Arrays.asList(
            new ChannelRange( 2,   4,  57000000, 6000000),
            new ChannelRange( 5,   6,  79000000, 6000000),
            new ChannelRange( 7,  13, 177000000, 6000000),
            new ChannelRange(14,  69, 473000000, 6000000)
        ));
        channelmaps.put(map.name, map);

        map = new ChannelMap("us-hrc");
        map.ranges.addAll(Arrays.asList(
            new ChannelRange(  2,   4,  55752700, 6000300),
            new ChannelRange(  5,   6,  79753900, 6000300),
            new ChannelRange(  7,  13, 175758700, 6000300),
            new ChannelRange( 14,  22, 121756000, 6000300),
            new ChannelRange( 23,  94, 217760800, 6000300),
            new ChannelRange( 95,  99,  91754500, 6000300),
            new ChannelRange(100, 135, 649782400, 6000300)
        ));
        channelmaps.put(map.name, map);

        map = new ChannelMap("us-irc");
        map.ranges.addAll(Arrays.asList(
            new ChannelRange(  2,   4,  57012500, 6000000),
            new ChannelRange(  5,   6,  81012500, 6000000),
            new ChannelRange(  7,  13, 177012500, 6000000),
            new ChannelRange( 14,  22, 123012500, 6000000),
            new ChannelRange( 23,  41, 219012500, 6000000),
            new ChannelRange( 42,  42, 333025000, 6000000),
            new ChannelRange( 43,  94, 339012500, 6000000),
            new ChannelRange( 95,  97,  93012500, 6000000),
            new ChannelRange( 98,  99, 111025000, 6000000),
            new ChannelRange(100, 135, 651012500, 6000000)
        ));
        channelmaps.put(map.name, map);

        map = new ChannelMap("us-cable");
        map.ranges.addAll(Arrays.asList(
            new ChannelRange(  2,   4,  57000000, 6000000),
            new ChannelRange(  5,   6,  79000000, 6000000),
            new ChannelRange(  7,  13, 177000000, 6000000),
            new ChannelRange( 14,  22, 123000000, 6000000),
            new ChannelRange( 23,  94, 219000000, 6000000),
            new ChannelRange( 95,  99,  93000000, 6000000),
            new ChannelRange(100, 135, 651000000, 6000000)
        ));
        map.submaps.add(channelmaps.get("us-irc"));
        map.submaps.add(channelmaps.get("us-hrc"));
        channelmaps.put(map.name, map);

    }

    private static class ChannelRange implements Serializable {
        private final static long serialVersionUID = 200905251503l;
        private short channelStart;
        private short channelEnd;
        private int   frequencyBase;
        private int   frequencySpacing;

        ChannelRange(int start, int end, int base, int spacing) {
            // cheap hack
            channelStart     = (short) start;
            channelEnd       = (short) end;

            frequencyBase    = base;
            frequencySpacing = spacing;
        }
    }

    public static class Channel implements Comparable<Channel>, Serializable {
        private final static long serialVersionUID = 200905251503l;
        private final Set<String> maps;

        private String modulation;
        public final short  number;
        public final int    frequency;

        // program association table crc
        private int patCRC;
        private int tsID;
        private List<Program> programs;

        public boolean equals(Object other) {
            if (other != null && other instanceof Channel) {
                if (frequency == ((Channel)other).frequency)
                    return true;
            }
            return false;
        }

        public int hashCode() {
            return frequency;
        }

        public int compareTo(Channel c) {
            return frequency - c.frequency;
        }

        public void setPatCRC(int crc) {
            patCRC = crc;
        }
        public int getPatCRC() {
            return patCRC;
        }

        public void setTsID(int id) {
            tsID = id;
        }
        public int getTsID() {
            return tsID;
        }

        public String getModulation() {
            return modulation;
        }

        public void setModulation(String mod) {
            modulation = mod;
        }

        public Channel(short number, int frequency, String map) {
            this.number = number;
            this.frequency = frequency - (frequency % FREQUENCY_RESOLUTION);

            maps = new HashSet<String>();
            maps.add(map);

            programs = new ArrayList<Program>();
        }

        public List<Program> getPrograms() {
            return programs;
        }
        public String toString() {
            return String.format(
                    "hz:%9d ch:%3d %s", frequency, number, maps);
        }

        public Set<String> getMaps() {
            return Collections.unmodifiableSet(maps);
        }

        public static class Program implements Serializable {
            private final static long serialVersionUID = 200905251503l;
            public final Channel channel;
            public final short number;
            public short virtualMajor;
            public short virtualMinor;
            private String name = "UNKNOWN";

            public Program(Channel c, short number, short major, short minor) {
                channel = c;
                this.number = number;
                virtualMajor = major;
                virtualMinor = minor;
            }

            public String getName() {
                return name;
            }
            public void setName(String name) {
                if (name != null)
                    this.name = name;
            }

            public String toString() {
                String virtual = virtualMinor == 0 ?
                        "" + virtualMajor : virtualMajor + "." + virtualMinor;
                return String.format("%d.%d [%s] %s",
                        channel.number, number, virtual, name);
            }
        }
    }

    public static ChannelMap forName(String name) {
        return channelmaps.get(name);
    }

    public static Set<String> mapNames() {
        return Collections.unmodifiableSet(channelmaps.keySet());
    }

    public List<Channel> getChannels() {
        if (channels != null)
            return channels;

        channels = new ArrayList<Channel>();
        channelMap = new HashMap<Integer,Channel>();

        for (ChannelRange range :  ranges) {
            int frequency = range.frequencyBase;
            for (short i = range.channelStart; i <= range.channelEnd; i++) {
                Channel c = new Channel(i, frequency, name);

                channelMap.put(frequency, c);
                channels.add(c);
                frequency += range.frequencySpacing;
            }
        }

        for (ChannelMap map : submaps) {
            List<Channel> ch = map.getChannels();
            for (Channel c : ch) {
                if (channelMap.containsKey(c.frequency)) {
                    Channel chan = channelMap.get(c.frequency);
                    chan.maps.add(map.name);
                } else {
                    channelMap.put(c.frequency, c);
                    channels.add(c);
                }
            }
        }

        Collections.sort(channels);
        return channels;
    }
    public static void main(String[] args) {
        for (Channel c : ChannelMap.forName("us-bcast").getChannels())
            System.out.println("us-bcast: " + c);
        for (Channel c : ChannelMap.forName("us-cable").getChannels())
            System.out.println("us-cable: " + c);
    }
}
