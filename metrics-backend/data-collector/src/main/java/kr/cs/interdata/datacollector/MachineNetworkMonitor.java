package kr.cs.interdata.datacollector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

//리눅스의 /proc/net/dev 파일을 읽어 각 네트워크 인터페이스의 누적 트래픽 정보를 JSON 형태로 반환
public class MachineNetworkMonitor {
    //호스트의 /proc/net/dev 파일을 컨테이너 내부에서 접근할 경로
    private static final String PROC_NET_DEV = "/host/proc/net/dev"; // 호스트에서 마운트한 경로

    public Map<String, Object> getNetworkInfoJson() {
        Map<String, Object> result = new LinkedHashMap<>();
        int idx = 0;

        List<String> lines;
        try {
            //host/proc/net/dev 파일의 모든 라인 읽기
            lines = Files.readAllLines(Paths.get(PROC_NET_DEV));
        } catch (IOException e) {
            // 파일 읽기 실패 시 빈 맵 반환
            return result;
        }

        // 첫 두 줄은 헤더이므로 건너뜀
        for (int i = 2; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;//빈 줄은 무시

            //각 라인을 :를 기준으로 분리(왼족: 인터페이스 이름, 오른쪽 : 데이터)
            String[] ifaceSplit = line.split(":");
            if (ifaceSplit.length < 2) continue;//이상한 라인 무시

            String iface = ifaceSplit[0].trim();//인터페이스 이름 추출
            String[] data = ifaceSplit[1].trim().split("\\s+");
            if (data.length < 16) continue;//데이터 필드 부족하면 무시

            try {
                //data[0]: 누적 수신 바이트
                //data[8]: 누적 송신 바이트
                long bytesReceived = Long.parseLong(data[0]);
                long bytesSent = Long.parseLong(data[8]);
                //인터페이스별 정보 맵 생성
                Map<String, Object> netInfo = new LinkedHashMap<>();
                netInfo.put("speedBps", null); // /proc/net/dev에는 속도 정보 없음
                netInfo.put("bytesReceived", bytesReceived);
                netInfo.put("bytesSent", bytesSent);
                //결과 맵에 "인터페이스명_인덱스"를 key로 추가
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
