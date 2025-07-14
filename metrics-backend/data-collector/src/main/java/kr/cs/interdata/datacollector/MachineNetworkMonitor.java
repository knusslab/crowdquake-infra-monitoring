package kr.cs.interdata.datacollector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class MachineNetworkMonitor {
    private static final String PROC_NET_DEV = "/host/proc/net/dev"; // 호스트에서 마운트한 경로

    public Map<String, Object> getNetworkInfoJson() {
        Map<String, Object> result = new LinkedHashMap<>();
        int idx = 0;

        List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(PROC_NET_DEV));
        } catch (IOException e) {
            // 파일 읽기 실패 시 빈 맵 반환
            return result;
        }

        // 첫 두 줄은 헤더이므로 건너뜀
        for (int i = 2; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;

            String[] ifaceSplit = line.split(":");
            if (ifaceSplit.length < 2) continue;

            String iface = ifaceSplit[0].trim();
            String[] data = ifaceSplit[1].trim().split("\\s+");
            if (data.length < 16) continue;

            try {
                long bytesReceived = Long.parseLong(data[0]);
                long bytesSent = Long.parseLong(data[8]);
                Map<String, Object> netInfo = new LinkedHashMap<>();
                netInfo.put("speedBps", null); // /proc/net/dev에는 속도 정보 없음
                netInfo.put("bytesReceived", bytesReceived);
                netInfo.put("bytesSent", bytesSent);
                result.put(iface + "_" + idx, netInfo);
                idx++;
            } catch (NumberFormatException e) {
                // 파싱 실패 시 해당 인터페이스는 건너뜀
            }
        }
        return result;
    }
}
//package kr.cs.interdata.datacollector;

//import oshi.SystemInfo;
//import oshi.hardware.HardwareAbstractionLayer;
//import oshi.hardware.NetworkIF;
//
//import java.util.LinkedHashMap;
//import java.util.List;
//import java.util.Map;
//
//public class MachineNetworkMonitor {
//    private final List<NetworkIF> networkIFS;
//
//    public MachineNetworkMonitor() {
//        SystemInfo si = new SystemInfo();
//        HardwareAbstractionLayer hal = si.getHardware();
//        this.networkIFS = hal.getNetworkIFs();
//    }
//
//    public Map<String, Object> getNetworkInfoJson() {
//        Map<String, Object> result = new LinkedHashMap<>();
//        int idx = 0;
//
//        for (NetworkIF net : networkIFS) {
//            net.updateAttributes();
//
//            // 활성화된 인터페이스만 포함
//            if (net.getIfOperStatus() == NetworkIF.IfOperStatus.UP) {
//                Map<String, Object> netInfo = new LinkedHashMap<>();
//                netInfo.put("speedBps", net.getSpeed()); // bps
//                netInfo.put("bytesReceived", net.getBytesRecv()); // byte
//                netInfo.put("bytesSent", net.getBytesSent()); // byte
//                result.put(net.getName() + "_" + idx, netInfo);
//                idx++;
//            }
//        }
//
//        return result;
//    }
//}
