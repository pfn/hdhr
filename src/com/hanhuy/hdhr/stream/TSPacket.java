package com.hanhuy.hdhr.stream;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.HashMap;

public class TSPacket {
    public final static int PACKET_SIZE = 188;

    public final static byte SYNC_BYTE = (byte) 0x47;

    public final static int TEI_FLAG  = 0x8000;
    public final static int PUSI_FLAG = 0x4000;

    public final static int ADAPTATION_PCR_FLAG     = 0x10;
    public final static int ADAPTATION_PCR_EXT_MASK = 0x1ff;
    public final static int ADAPTATION_FLAG         = 0x20;
    public final static int PAYLOAD_FLAG            = 0x10;
    public final static int CONTINUITY_COUNTER_MASK = 0x0f;

    public final static int LENGTH_MASK = 0x0fff;
    public final static int VERSION_MASK = 0x3e;

    public final static int PID_MASK = 0x1fff;
    public final static short NULL_PID = 0x1fff;

    public final static short PAT_PID = 0x0000;

    public final static int PAT_ID = 0x00;
    public final static int PMT_ID = 0x02;

    private boolean hasTEI;
    private boolean hasPUSI;
    private byte continuityCounter;
    private boolean hasAdaptationField;
    private boolean hasPayloadData;
    private short pid;
    private long pcr;
    private short pcrPID;
    private short pmtProgramNumber;
    private boolean pmtRead;
    private boolean pcrRead;
    private boolean pmtHasAudio;
    private boolean pmtHasVideo;

    private ByteBuffer packet;

    private Map<Short,Short> programPmtMap;
    private Map<Short,Byte> pmtMap;

    private enum StreamType { AUDIO, VIDEO, UNKNOWN }

    private byte tableID;

    public boolean parse(byte[] buf, int off, int len) {
        return parse(ByteBuffer.wrap(buf, off, len));
    }

    public boolean parse(ByteBuffer packet) {
        byte b = packet.get();
        if (b != SYNC_BYTE) {
            throw new IllegalStateException("Not a sync byte: " + b);
        }
        short s = packet.getShort();
        hasTEI  = (s & TEI_FLAG)  != 0;
        hasPUSI = (s & PUSI_FLAG) != 0;

        pid = (short) (s & PID_MASK);

        b = packet.get();
        hasAdaptationField = (b & ADAPTATION_FLAG) != 0;
        hasPayloadData     = (b & PAYLOAD_FLAG)    != 0;
        continuityCounter  = (byte) (b & CONTINUITY_COUNTER_MASK);
        this.packet = packet;

        programPmtMap = null;
        tableID = -1;
        pcrPID  = -1;
        pmtRead = false;
        pmtMap  = null;
        pmtProgramNumber = -1;
        pmtHasAudio = false;
        pmtHasVideo = false;
        pcrRead = false;
        pcr     = -1;
        return true;
    }

    public boolean isPAT() {
        return hasPUSI && hasPayloadData && pid == PAT_PID;
    }

    public boolean hasPUSI() {
        return hasPUSI;
    }

    public boolean isPMT() {
        if (tableID == PMT_ID)
            return true;
        if (tableID != -1)
            return false;

        tableID = (byte) 0xff;

        if (hasAdaptationField) {
            int length = packet.get() & 0xff;
            packet.position(packet.position() + length);
        }
        if (hasPayloadData) {
            packet.get(); // pointer;
            tableID = packet.get();
        }
        return tableID == PMT_ID;
    }

    private void readPMT() {
        if (pmtRead)
            return;
        if (!isPMT())
            return;

        pmtRead = true;

        short length = (short) (packet.getShort() & LENGTH_MASK);
        pmtProgramNumber = packet.getShort();
        short version = (byte) (packet.get() & VERSION_MASK);
        packet.get(); // skip section & last section bytes
        packet.get(); // skip section & last section bytes
        pcrPID = (short) (packet.getShort() & PID_MASK);

        short infoLength = (short) (packet.getShort() & LENGTH_MASK);

        length -= 2 + 1 + 1 + 1 + 2 + 2; // account for above items

        // skip packet info descriptor
        if (infoLength > 0) {
            packet.position(packet.position() + infoLength);
            length -= infoLength;
        }

        length -= 4; // account for 32bit crc at end

        pmtMap = new HashMap<Short,Byte>();
        while (length > 0) {
            byte type = packet.get();

            short epid = (short) (packet.getShort() & PID_MASK);
            short esInfoLength = (short) (packet.getShort() & LENGTH_MASK);
            length -= 5; // account for above

            // skip ES info descriptors
            packet.position(packet.position() + esInfoLength);
            length -= esInfoLength;

            StreamType t = getStreamType(type);
            pmtHasAudio |= t == StreamType.AUDIO;
            pmtHasVideo |= t == StreamType.VIDEO;

            pmtMap.put(epid, type);
        }
    }

    public boolean hasPID(short pid) {
        return this.pid == pid;
    }

    public boolean hasPayloadData() {
        return hasPayloadData;
    }

    public boolean hasAdaptationField() {
        return hasAdaptationField;
    }

    public byte getContinuityCounter() {
        return continuityCounter;
    }

    public short getPID() {
        return pid;
    }

    public boolean hasVideoStream() {
        if (!isPMT())
            throw new IllegalStateException("not a PMT packet");
        readPMT();
        return pmtHasVideo;
    }

    public boolean hasAudioStream() {
        if (!isPMT())
            throw new IllegalStateException("not a PMT packet");
        readPMT();
        return pmtHasAudio;
    }

    public Map<Short,Byte> getPMT() {
        if (!isPMT())
            throw new IllegalStateException("not a PMT packet");
        readPMT();
        return pmtMap;
    }
    public short getPCRPID() {
        if (!isPMT())
            return -1;
        readPMT();
        return pcrPID;
    }

    public Map<Short,Short> getPAT() {
        if (!isPAT()) {
            return null;
        }
        if (programPmtMap == null) {
            programPmtMap = new HashMap<Short,Short>();
            if (hasAdaptationField) {
                int length = packet.get() & 0xff;
                packet.position(packet.position() + length);
            }

            packet.get(); // pointer
            tableID = packet.get();
            if (tableID != PAT_ID)
                return null;
            short length = (short) (packet.getShort() & LENGTH_MASK);
            length -= 9; // 4 byte crc at end, 5 bytes we skip
            // 5 bytes skipped are: tsid(2) version(1), sect(1) last_sect(1)
            packet.position(packet.position() + 5);

            for (int i = 0; i < length / 4; i++) {
                short number = packet.getShort();
                short pid = (short) (packet.getShort() & PID_MASK);
                programPmtMap.put(number, pid);
            }
        }
        return programPmtMap;
    }

    private void readPCR() {
        if (pcrRead)
            return;
        if (!hasAdaptationField)
            return;

        pcrRead = true;

        int length = packet.get() & 0xff;
        if (length == 0)
            return;

        byte flags = packet.get();
        length--;

        if ((flags & ADAPTATION_PCR_FLAG) == 0)
            return;

        long base  = packet.getInt() & 0xffffffffL;
        int extPCR = packet.getShort() & 0xffff;

        base <<= 1;
        base |= (extPCR >> 15);

        int ext = extPCR & ADAPTATION_PCR_EXT_MASK;
        pcr = base * 300 + ext;
    }
    /**
     * @return a 27mhz clock counter
     */
    public long getPCR() {
        readPCR();
        return pcr;
    }

    private StreamType getStreamType(byte type) {
        StreamType streamtype = null;
        int t = type & 0xff;

        switch (t) {

        case 0x01: // ISO/IEC 11172 Video
        case 0x02: // ITU-T Rec. H.262 | ISO/IEC 13818-2 Video
        case 0X1B: // AVC video stream
        case 0x80: // DigiCipher® II video
        case 0x10: // ISO/IEC 14496-2 Visual
            streamtype = StreamType.VIDEO;
            break;

        case 0x03: // ISO/IEC 11172 Audio
        case 0x04: // ISO/IEC 13818-3 Audio
        case 0x81: // ATSC A/53 audio
        case 0x0F: // ISO/IEC 13818-7 Audio (AAC) with ADTS transport
        case 0x11: // ISO/IEC 14496-3 Audio with the LATM transport syntax as defined in ISO/IEC 14496-3
            streamtype = StreamType.AUDIO;
            break;

        case 0x05: // ITU-T Rec. H.222.0 | ISO/IEC 13818-1 private sections
        case 0x06: // ITU-T Rec. H.222.0 | ISO/IEC 13818-1 PES packets containing private data
        case 0x07: // ISO/IEC 13522 MHEG
        case 0x08: // ITU-T Rec. H.222.0 | ISO/IEC 13818-1 DSM-CC
        case 0x09: // ITU-T Rec. H.222.0 | ISO/IEC 13818-1/11172-1 auxiliary
        case 0x0A: // ISO/IEC 13818-6 Multi-protocol Encapsulation
        case 0x0B: // ISO/IEC 13818-6 DSM-CC U-N Messages
        case 0x0C: // ISO/IEC 13818-6 Stream Descriptors
        case 0x0D: // ISO/IEC 13818-6 Sections
        case 0x0E: // ISO/IEC 13818-1 auxiliary
        case 0x12: // ISO/IEC 14496-1 SL-packetized stream or FlexMux stream carried in PES packets
        case 0x13: // ISO/IEC 14496-1 SL-packetized stream or FlexMux stream carried in ISO/IEC 14496_sections
        case 0x14: // ISO/IEC 13818-6 DSM-CC Synchronized Download Protocol
        case 0x15: // Metadata carried in PES packets
        case 0x16: // Metadata carried in metadata_sections
        case 0x17: // Metadata carried in ISO/IEC 13818-6 Data Carousel
        case 0x18: // Metadata carried in ISO/IEC 13818-6 Object Carousel
        case 0x19: // Metadata carried in ISO/IEC 13818-6 Synchronized Download Protocol
        case 0x1A: // IPMP stream (defined in ISO/IEC 13818-11, MPEG-2 IPMP)
        case 0x82: // SCTE Standard Subtitle
        case 0x83: // SCTE Isochronous Data | Reserved
        case 0x86: // SCTE 35 splice_information_table | [Cueing]
        case 0x87: // E-AC-3
        case 0x90: // DVB stream_type value for Time Slicing
        case 0x91: // IETF Unidirectional Link Encapsulation (ULE)
        case 0x95: // ATSC Data Service Table, Network Resources Table
        case 0xA0: // (Conflict: DVS-022 states 0xA0 used by non-broadcast
        case 0xC2: // ATSC synchronous data stream
        case 0xC3: // SCTE Asynchronous Data
        case 0xEA: // VC-1 Elementary Stream
            streamtype = StreamType.UNKNOWN;
            break;

        case 0x00: // ITU-T | ISO/IEC Reserved
        case 0x84: // ATSC Reserved
        case 0x85: // ATSC Program Identifier , SCTE Reserved
        case 0x88: // ATSC Reserved
        case 0x89: // ATSC Reserved
            streamtype = StreamType.UNKNOWN;
            break;

        default:
        //case 0x1C: // – 0x7F ITU-T Rec. H.222.0 | ISO/IEC 13818-1 Reserved
        //case 0x87-0x9F: // SCTE Reserved
        //case 0x8A: // – 0x8F ATSC Reserved entered 2
        //case 0x92: // –0x94 ATSC Reserved
        //case 0x96: // – 0xC1 ATSC Reserved
        //case 0xC4: // – 0xE9 ATSC User Private
            streamtype = StreamType.UNKNOWN;
        }
        return streamtype;
    }
}
