package functions;

import utils.SystemClock;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

public class PESPacketInfo {
    private long PTS = 0;
    private long streamID = 0;
    private String AUType = "";

    public void setPTS(long PTS) {
        this.PTS = PTS;
    }

    public long getPTS() {
        return this.PTS;
    }

    public void setStreamID(long streamID) {
        this.streamID = streamID;
    }

    public long getStreamID() {
        return this.streamID;
    }

    public void setAUType(String auType) {
        this.AUType = auType;
    }

    public String getAUType() {
        return this.AUType;
    }

    public static long readFile(RandomAccessFile fileHandle, long startPos, int width) throws IOException {
        fileHandle.seek(startPos);
        byte[] bytes;
        switch (width) {
            case 4:
                bytes = new byte[4];
                if (fileHandle.read(bytes) != 4) {
                    throw new IOException();
                }
                return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).getInt();
            case 2:
                bytes = new byte[2];
                if (fileHandle.read(bytes) != 2) {
                    throw new IOException();
                }
                return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).getShort() & 0xFFFF;
            case 1:
                bytes = new byte[1];
                if (fileHandle.read(bytes) != 1) {
                    throw new IOException();
                }
                return bytes[0] & 0xFF;
            default:
                throw new IllegalArgumentException("Invalid width: " + width);
        }
    }

    public static long[] parseAdaptationField(RandomAccessFile fileHandle, long startPos, SystemClock PCR) throws IOException {
        long n = (long) startPos;
        long flags = 0;
        long adaptationFieldLength = readFile(fileHandle, n, 1);
        if (adaptationFieldLength > 0) {
            flags = readFile(fileHandle, n + 1, 1);
            long PCRFlag = (flags >> 4) & 0x1;
            if (PCRFlag == 1) {
                long time1 = readFile(fileHandle, n + 2, 1);
                long time2 = readFile(fileHandle, n + 3, 1);
                long time3 = readFile(fileHandle, n + 4, 1);
                long time4 = readFile(fileHandle, n + 5, 1);
                long time5 = readFile(fileHandle, n + 6, 1);
                long time6 = readFile(fileHandle, n + 7, 1);

                long PCRVal = ((long) time1 << 25)
                        | ((long) time2 << 17)
                        | ((long) time3 << 9)
                        | ((long) time4 << 1)
                        | ((long) (time5 & 0x80) >> 7);

                PCRVal *= 300;
                PCRVal |= ((time5 & 0x01) << 8) | time6;

                PCR.setPCR(PCRVal);
            }
        }
        return new long[]{adaptationFieldLength + 1, flags};
    }

    public static long getPTS(RandomAccessFile fileHandle, long startPos) throws IOException {
        long n = (long) startPos;

        long time1 = readFile(fileHandle, n, 1);
        long time2 = readFile(fileHandle, n + 1, 1);
        long time3 = readFile(fileHandle, n + 2, 1);
        long time4 = readFile(fileHandle, n + 3, 1);
        long time5 = readFile(fileHandle, n + 4, 1);

        long PTS = ((time1 & 0x0E) >> 1) << 8 | time2;
        PTS = (PTS << 7) | ((time3 & 0xFE) >> 1);
        PTS = (PTS << 8) | time4;
        PTS = (PTS << 7) | ((time5 & 0xFE) >> 1);

        return PTS;
    }

    public static String parseIndividualPESPayload(RandomAccessFile fileHandle, long startPos) throws IOException {
        long n = startPos;
        long local = readFile(fileHandle, n, 4);
        long k = 0;
        while ((local & 0xFFFFFF00) != 0x00000100) {
            k += 1;
            if (k > 100) {
                return "Unknown AU type";
            }
            local = readFile(fileHandle, n + k, 4);
        }

        if (((local & 0xFFFFFF00) == 0x00000100) && ((local & 0x1F) == 0x9)) {
            long primary_pic_type = readFile(fileHandle, n + k + 4, 1);
            primary_pic_type = (primary_pic_type & 0xE0) >> 5;
            if (primary_pic_type == 0x0) {
                return "IDR_picture";
            } else {
                return "non_IDR_picture";
            }
        }
        return "";
    }

    public static void parsePESHeader(RandomAccessFile fileHandle, long startPos, PESPacketInfo PESPktInfo) throws IOException {
        long n = startPos;
        long stream_ID = readFile(fileHandle, n + 3, 1);
        long PES_packetLength = readFile(fileHandle, n + 4, 2);
        PESPktInfo.setStreamID(stream_ID);

        long k = 6;

        if ((stream_ID != 0xBC) &&
                (stream_ID != 0xBE) &&
                (stream_ID != 0xF0) &&
                (stream_ID != 0xF1) &&
                (stream_ID != 0xFF) &&
                (stream_ID != 0xF9) &&
                (stream_ID != 0xF8)) {

            long PES_packet_flags = readFile(fileHandle, n + 5, 4);
            long PTS_DTS_flag = ((PES_packet_flags >> 14) & 0x3);
            long PES_header_data_length = PES_packet_flags & 0xFF;

            k += PES_header_data_length + 3;

            if (PTS_DTS_flag == 0x2) {
                long PTS = getPTS(fileHandle, n + 9);
                PESPktInfo.setPTS(PTS);
            } else if (PTS_DTS_flag == 0x3) {
                long PTS = getPTS(fileHandle, n + 9);
                PESPktInfo.setPTS(PTS);

                long DTS = getPTS(fileHandle, n + 14);
            } else {
                k = k;
                return;
            }

            String auType = parseIndividualPESPayload(fileHandle, n + k);
            PESPktInfo.setAUType(auType);
        }
    }

    public static void parsePATSection(RandomAccessFile fileHandle, long k) throws IOException {
        long table_id = (long) (readFile(fileHandle, k, 4) >> 24);
        if (table_id != 0x0) {
            //System.err.prlongln("Ooops! error in parsePATSection()!");
            return;
        }

        //System.out.prlongln("------- PAT Information -------");
        long section_length = (long) ((readFile(fileHandle, k, 4) >> 8) & 0xFFF);
        //System.out.prlongln("section_length = " + section_length);

        long transport_stream_id = (long) ((readFile(fileHandle, k + 4, 4) & 0xFF) << 8);
        transport_stream_id += (long) ((readFile(fileHandle, k + 4, 4) >> 16) & 0xFF);
        long version_number = (long) ((readFile(fileHandle, k + 4, 4) >> 17) & 0x1F);
        long current_next_indicator = (long) ((readFile(fileHandle, k + 4, 4) >> 16) & 0x1);
        long section_number = (long) ((readFile(fileHandle, k + 4, 4) >> 8) & 0xFF);
        long last_section_number = (long) (readFile(fileHandle, k + 4, 4) & 0xFF);
        //System.out.prlongln("section_number = " + section_number + ", last_section_number = " + last_section_number);

        long length = section_length - 4 - 5;
        long j = k + 8;

        while (length > 0) {
            long program_number = (long) (readFile(fileHandle, j, 4) >> 16);
            long program_map_PID = (long) (readFile(fileHandle, j, 4) & 0x1FFF);
            //System.out.prlongln("program_number = 0x" + Long.toHexString(program_number));
            if (program_number == 0) {
                //System.out.prlongln("network_PID = 0x" + Long.toHexString(program_map_PID));
            } else {
                //System.out.prlongln("program_map_PID = 0x" + Long.toHexString(program_map_PID));
            }
            length -= 4;
            j += 4;
            //System.out.prlongln("");
        }
    }

    public static void parsePMTSection(RandomAccessFile fileHandle, long k) throws IOException {
        long local = readFile(fileHandle, k, 4);
        long table_id = (local >> 24);
        if (table_id != 0x2) {
            //System.err.prlongln("Ooops! error in parsePATSection()!");
            return;
        }

        //System.out.prlongln("------- PMT Information -------");

        long section_length = (local >> 8) & 0xFFF;
        //System.out.prlongf("section_length = %d\n", section_length);

        long program_number = (local & 0xFF) << 8;

        local = readFile(fileHandle, k + 4, 4);

        program_number += (local >> 24) & 0xFF;
        //System.out.prlongf("program_number = %d\n", program_number);

        long version_number = (local >> 17) & 0x1F;
        long current_next_indicator = (local >> 16) & 0x1;
        long section_number = (local >> 8) & 0xFF;
        long last_section_number = local & 0xFF;
        //System.out.prlongf("section_number = %d, last_section_number = %d\n", section_number, last_section_number);

        local = readFile(fileHandle, k + 8, 4);

        long PCR_PID = (local >> 16) & 0x1FFF;
        //System.out.prlongf("PCR_PID = 0x%X\n", PCR_PID);
        long program_info_length = (local & 0xFFF);
        //System.out.prlongf("program_info_length = %d\n", program_info_length);

        long n = program_info_length;
        long m = k + 12;
        while (n > 0) {
            long descriptor_tag = readFile(fileHandle, m, 1);
            long descriptor_length = readFile(fileHandle, m + 1, 1);
            //System.out.prlongf("descriptor_tag = %d, descriptor_length = %d\n", descriptor_tag, descriptor_length);
            n -= descriptor_length + 2;
            m += descriptor_length + 2;
        }

        long j = k + 12 + program_info_length;
        long length = section_length - 4 - 9 - program_info_length;

        while (length > 0) {
            long local1 = readFile(fileHandle, j, 1);
            long local2 = readFile(fileHandle, j + 1, 4);

            long stream_type = local1;
            long elementary_PID = (local2 >> 16) & 0x1FFF;
            long ES_info_length = local2 & 0xFFF;

            //System.out.prlongf("stream_type = 0x%X, elementary_PID = 0x%X, ES_info_length = %d\n", stream_type, elementary_PID, ES_info_length);
            n = ES_info_length;
            m = j + 5;
            while (n > 0) {
                long descriptor_tag = readFile(fileHandle, m, 1);
                long descriptor_length = readFile(fileHandle, m + 1, 1);
                //System.out.prlongf("descriptor_tag = %d, descriptor_length = %d\n", descriptor_tag, descriptor_length);
                n -= descriptor_length + 2;
                m += descriptor_length + 2;
            }

            j += 5 + ES_info_length;
            length -= 5 + ES_info_length;
        }

        //System.out.prlongln("");
    }

    public static void parseSITSection(RandomAccessFile fileHandle, long k) throws IOException {
        long local = readFile(fileHandle, k, 4);

        long table_id = (local >> 24);
        if (table_id != 0x7F) {
            //System.out.prlongln("Ooops! error in parseSITSection()!");
            return;
        }

        //System.out.prlongln("------- SIT Information -------");

        long section_length = (local >> 8) & 0xFFF;
        //System.out.prlongln("section_length = " + section_length);
        local = readFile(fileHandle, k + 4, 4);

        long section_number = (local >> 8) & 0xFF;
        long last_section_number = local & 0xFF;
        //System.out.prlongln("section_number = " + section_number + ", last_section_number = " + last_section_number);
        local = readFile(fileHandle, k + 8, 2);
        long transmission_info_loop_length = local & 0xFFF;
        //System.out.prlongln("transmission_info_loop_length = " + transmission_info_loop_length);

        long n = transmission_info_loop_length;
        long m = k + 10;
        while (n > 0) {
            long descriptor_tag = readFile(fileHandle, m, 1);
            long descriptor_length = readFile(fileHandle, m + 1, 1);
            //System.out.prlongln("descriptor_tag = " + descriptor_tag + ", descriptor_length = " + descriptor_length);
            n -= descriptor_length + 2;
            m += descriptor_length + 2;
        }

        long j = k + 10 + transmission_info_loop_length;
        long length = section_length - 4 - 7 - transmission_info_loop_length;

        while (length > 0) {
            long local1 = readFile(fileHandle, j, 4);
            long service_id = (local1 >> 16) & 0xFFFF;
            long service_loop_length = local1 & 0xFFF;
            //System.out.prlongln("service_id = " + service_id + ", service_loop_length = " + service_loop_length);

            n = service_loop_length;
            m = j + 4;
            while (n > 0) {
                long descriptor_tag = readFile(fileHandle, m, 1);
                long descriptor_length = readFile(fileHandle, m + 1, 1);
                //System.out.prlongln("descriptor_tag = " + descriptor_tag + ", descriptor_length = " + descriptor_length);
                n -= descriptor_length + 2;
                m += descriptor_length + 2;
            }

            j += 4 + service_loop_length;
            length -= 4 + service_loop_length;
        }
        //System.out.prlongln("");
    }

    public static ArrayList<Double> getDeltaPcrPts(int pid, ArrayList<LinkedHashMap<String, Object>> pcr, ArrayList<LinkedHashMap<String, Object>> pts) {
        ArrayList<Double> listDelta = new ArrayList<>();
        int pcrIdx = 0;

        for (LinkedHashMap<String, Object> packet : pts) {
            if (!(((Long) packet.get("pid")).intValue() == pid)) {
                continue;
            }
            while ((((Long) pcr.get(pcrIdx).get("packet")).intValue() < ((Long)packet.get("packet")).intValue()) && (pcrIdx < pcr.size() - 1)) {
                pcrIdx++;
            }
            if (((Long) pcr.get(pcrIdx).get("packet")).intValue() < ((Long) packet.get("packet")).intValue()) {
                break;
            }
            listDelta.add((((Long) packet.get("pts")).doubleValue() / 90 - ((Long) pcr.get(pcrIdx).get("pcr")).doubleValue() / 27000));
        }
        return listDelta;
    }

    public static LinkedHashMap<String, Long> getDeltaStats(ArrayList<Double> listDelta) {
        double total = 0;
        long minVal = 100000;
        long maxVal = 0;

        for (double delta : listDelta) {
            total += delta;
            if (delta < minVal) {
                minVal = (long) delta;
            }
            if (delta > maxVal) {
                maxVal = (long) delta;
            }
        }
        LinkedHashMap<String, Long> result = new LinkedHashMap<String, Long>();

        result.put("min", minVal);
        result.put("max", maxVal);
        result.put("average", (long) (total / listDelta.size()));
        return result;
    }

    public static LinkedHashMap<String, Long> getTrackStat(long pid, long count, ArrayList<LinkedHashMap<String, Object>> pts) {
        int firstPacket = 0;
        int lastPacket = pts.size() - 1;

        while (((Long) pts.get(firstPacket).get("pid")).intValue() != pid) {
            firstPacket++;
        }

        while (((Long)pts.get(lastPacket).get("pid")).intValue() != pid) {
            lastPacket--;
        }

        double duration = ((Long) pts.get(lastPacket).get("pts")).doubleValue() / 90 - ((Long) pts.get(firstPacket).get("pts")).doubleValue() / 90;
        long size = count * 188;

        LinkedHashMap<String, Long> result = new LinkedHashMap<String, Long>();
        result.put("duration", (long) (duration / 1000));
        result.put("size", size);
        result.put("bandwidth", (long) (8 * 1000 * size / duration));
        return result;

    }

    public static ArrayList<Map<String, Object>> getPidStats(ArrayList<LinkedHashMap<String, Object>> pidList, ArrayList<LinkedHashMap<String, Object>> pcr, ArrayList<LinkedHashMap<String, Object>> pts) {
        ArrayList<Map<String, Object>> stats = new ArrayList<>();

        for (Map<String, Object> pid : pidList) {
            ArrayList<Double> deltaPid = getDeltaPcrPts(((Long) pid.get("pid")).intValue(), pcr, pts);
            Map<String, Long> deltaStats = getDeltaStats(deltaPid);
            Map<String, Long> stat = getTrackStat(((Long) pid.get("pid")), ((Integer)pid.get("count")).longValue(), pts);

            Map<String, Object> pidStats = new LinkedHashMap<>();
            pidStats.put("pid", pid.get("pid"));
            pidStats.put("deltaPcrPts", deltaStats);
            pidStats.put("duration", stat.get("duration"));
            pidStats.put("size", stat.get("size"));
            pidStats.put("bandwidth", stat.get("bandwidth"));

            stats.add(pidStats);
        }

        return stats;
    }

    public static ArrayList<ArrayList<LinkedHashMap<String, Object>>> parsePcrPts(RandomAccessFile fileHandle) {
        SystemClock PCR = new SystemClock();
        PESPacketInfo PESPktInfo = new PESPacketInfo();
        long n = 0;
        long packet_size = 188;
        long packetCount = 0;
        ArrayList<LinkedHashMap<String, Object>> PESPidList = new ArrayList<LinkedHashMap<String, Object>>();
        ArrayList<LinkedHashMap<String, Object>> PTSList = new ArrayList<LinkedHashMap<String, Object>>();
        ArrayList<LinkedHashMap<String, Object>> PCRList = new ArrayList<LinkedHashMap<String, Object>>();

        try {
            while (true) {
                long PacketHeader = readFile(fileHandle, n, 4);
                long syncByte = (PacketHeader >> 24);
                if (syncByte != 0x47) {
                    //System.out.prlongln("Ooops! Can NOT found Sync_Byte! maybe something wrong with the file");
                    break;
                }
                long payload_unit_start_indicator = (PacketHeader >> 22) & 0x1;
                long PID = ((PacketHeader >> 8) & 0x1FFF);
                long adaptation_fieldc_trl = ((PacketHeader >> 4) & 0x3);
                long Adaptation_Field_Length = 0;
                if ((adaptation_fieldc_trl == 0x2) | (adaptation_fieldc_trl == 0x3)) {
                    long[] result = parseAdaptationField(fileHandle, n + 4, PCR);
                    Adaptation_Field_Length = result[0];
                    long flags = result[1];
                    if (((flags >> 4) & 0x1) != 0) {
                        boolean discontinuity = false;
                        if (((flags >> 7) & 0x1) != 0) {
                            discontinuity = true;
                        }
                        //System.out.prlongln("PCR packet, packet No. " + packetCount + ", PID = 0x" + Long.toHexString(PID) +
                              //  ", PCR = 0x" + Long.toHexString(PCR.getPCR()) + ", discontinuity = " + discontinuity);
                        LinkedHashMap<String, Object> pcrMap = new LinkedHashMap<String, Object>();
                        pcrMap.put("packet", packetCount);
                        pcrMap.put("pid", PID);
                        pcrMap.put("pcr", PCR.getPCR());
                        pcrMap.put("discontinuity", discontinuity);
                        PCRList.add(pcrMap);
                    }
                }
                if ((adaptation_fieldc_trl == 0x1) | (adaptation_fieldc_trl == 0x3)) {
                    long PESstartCode = readFile(fileHandle, n + Adaptation_Field_Length + 4, 4);
                    if ((PESstartCode & 0xFFFFFF00) == 0x00000100) {
                        if (payload_unit_start_indicator == 1) {
                            parsePESHeader(fileHandle, n + Adaptation_Field_Length + 4, PESPktInfo);
                            //System.out.prlongln("PES start, packet No. " + packetCount + ", PID = 0x" +
                                //    Long.toHexString(PID) + ", PTS = 0x" + Long.toHexString(PESPktInfo.PTS));
                            LinkedHashMap<String, Object> ptsMap = new LinkedHashMap<String, Object>();
                            ptsMap.put("packet", packetCount);
                            ptsMap.put("pid", PID);
                            ptsMap.put("pts", PESPktInfo.PTS);
                            PTSList.add(ptsMap);
                        }
                        boolean pidFound = false;
                        for (Map<String, Object> element : PESPidList) {
                            if ((long) element.get("pid") == PID) {
                                pidFound = true;
                                break;
                            }
                        }

                        if (!pidFound) {
                            LinkedHashMap<String, Object> pesPidInfo = new LinkedHashMap<String, Object>();
                            pesPidInfo.put("pid", PID);
                            pesPidInfo.put("count", 0);
                            PESPidList.add(pesPidInfo);
                        }

                    } else if (((PESstartCode & 0xFFFFFF00) != 0x00000100) && (payload_unit_start_indicator == 1)) {

                        long polonger_field = (PESstartCode >> 24);
                        long table_id = readFile(fileHandle, n + Adaptation_Field_Length + 4 + 1 + polonger_field, 1);

                        if ((table_id == 0x0) && (PID != 0x0)) {
                            //System.out.prlongln("Ooops!, Something wrong in packet No. " + packetCount);
                        }

                        long k = n + Adaptation_Field_Length + 4 + 1 + polonger_field;

                        if (table_id == 0x0) {
                            //System.out.prlongln("pasing PAT Packet! packet No. " + packetCount + ", PID = 0x" + Long.toHexString(PID));
                            parsePATSection(fileHandle, k);
                        } else if (table_id == 0x2) {
                            //System.out.prlongln("pasing PMT Packet! packet No. " + packetCount + ", PID = 0x" + Long.toHexString(PID));
                            parsePMTSection(fileHandle, k);
                        } else if (table_id == 0x7F) {
                            //System.out.prlongln("pasing SIT Packet! packet No. " + packetCount + ", PID = 0x" + Long.toHexString(PID));
                            parseSITSection(fileHandle, k);
                        }
                    }
                }
                n += packet_size;
                for (LinkedHashMap<String, Object> pesPidInfo : PESPidList) {
                    if ((long) pesPidInfo.get("pid") == PID) {
                        pesPidInfo.put("count", (int) pesPidInfo.get("count") + 1);
                        break;
                    }
                }

                packetCount++;
            }

        } catch (IOException e) {
            //System.out.prlongln("IO error! maybe reached EOF");
            return new ArrayList<>(Arrays.asList(PESPidList, PCRList, PTSList));
        } finally {
            try {
                if (fileHandle != null)
                    fileHandle.close();
            } catch (IOException e) {
                //System.out.prlongln("Error closing fileHandle: " + e.getMessage());
            }
        }

        return new ArrayList<>(Arrays.asList(PESPidList, PCRList, PTSList));
    }

    public static void parseTransportStream(String filename) throws IOException {
        RandomAccessFile fileHandle = new RandomAccessFile(filename, "r");

        ArrayList<ArrayList<LinkedHashMap<String, Object>>> data_list;

        try {
            data_list = parsePcrPts(fileHandle);
            ArrayList<LinkedHashMap<String, Object>> pesPidList= data_list.get(0);

            ArrayList<LinkedHashMap<String, Object>> pcr= data_list.get(1);

            ArrayList<LinkedHashMap<String, Object>> pts= data_list.get(2);

            ArrayList<Map<String, Object>> stats = getPidStats(pesPidList,pcr,pts);
            System.out.println(stats);

        }catch (Exception e){
            e.printStackTrace();
        }
        finally {
            fileHandle.close();
        }
    }
}
