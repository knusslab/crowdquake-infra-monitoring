package kr.cs.interdata.datacollector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * ┌───────────── ContainerResourceMonitor에서 사용하는 시스템 파일/경로 정리 ───────────────┐
 *
 * [1] /sys/fs/cgroup/cpuacct/cpuacct.usage (cgroup v1)
 *    - 컨테이너 혹은 프로세스 그룹이 누적해서 사용한 CPU 시간(ns, 나노초 단위)
 *    - 파일 내용: 숫자 문자열 한 줄 (예: "18273645100000")
 *    - 사용 방식: 값이 있으면 cgroup v1, 없으면 cgroup v2로 판단
 *
 * [2] /sys/fs/cgroup/cpu.stat (cgroup v2)
 *    - CPU 사용량 및 통계 정보 (여러 줄로 구성)
 *    - 예시:
 *         usage_usec 47240196
 *         user_usec  28380192
 *         system_usec 18860004
 *    - 주요 필드:
 *        usage_usec: 전체 CPU 사용량(us, 마이크로초) → 1000을 곱해 ns로 변환
 *        user_usec, system_usec: 각 모드별 CPU 시간
 *
 * [3] /sys/fs/cgroup/memory/memory.usage_in_bytes (cgroup v1)
 *    - 현재 컨테이너의 메모리 사용량(실시간, 바이트 단위)
 *    - 파일 내용: 숫자 하나 (예: "50421248")
 *
 * [4] /sys/fs/cgroup/memory.current (cgroup v2)
 *    - 메모리 사용량 (단위: 바이트)
 *    - 위 파일 없으면 이걸 읽음
 *
 * [5] /sys/fs/cgroup/blkio/io_service_bytes_recursive (cgroup v1)
 *    - 각 블록 장치별 누적 디스크 I/O 정보
 *    - 파일 예시:
 *        8:0 Read 123456
 *        8:0 Write 789012
 *        8:0 Sync 876
 *    - [8:0]은 디바이스 major:minor, [Read/Write]는 작업 종류, 마지막은 바이트 수
 *    - 코드에선 Read/Write 합만 집계, Sync 등은 사용하지 않음
 *
 * [6] /sys/fs/cgroup/io.stat (cgroup v2)
 *    - 디스크 I/O 통계 (블록 장치별 한 줄)
 *      예: dev 8:0 rbytes=987654 wbytes=123456 rios=12 wios=34 ...
 *    - rbytes: 누적 읽기 바이트, wbytes: 누적 쓰기 바이트 (코드는 이 두 값만 사용)
 *    - rios, wios: I/O 요청 횟수 등 다른 통계 정보도 포함
 *
 * [7] /proc/net/dev
 *    - 컨테이너에서 볼 수 있는 네트워크 인터페이스별 누적 트래픽 통계
 *    - 헤더 2줄 + [iface명: 수신8개 송신8개]
 *      예: eth0: 12345678 123 0 0 0 0 0 0 87654321 456 0 0 0 0 0 0
 *      [0] 수신 바이트, [8] 송신 바이트
 *      [1]/[9] 패킷, [2]/[10] 오류, [3]/[11] 드롭 등
 *    - 코드에선 [0]수신, [8]송신 이 두 값만 직접 활용
 *
 * ※ cgroup v1/v2 상황에 따라 파일 위치와 필드/포맷이 달라 모두 지원하게 작성됨
 * ※ 누적값이므로 변화량(증분, delta)은 외부 루프에서 계산해야 실제 사용량/속도 집계 가능
 *
 * ───────────────────────────────────────────────────────────────
 * 확장/분석에 활용 가능한 추가 정보 (현 코드 미사용):
 *  - /sys/fs/cgroup/cpuacct/cpuacct.stat, cpu.stat: user/system 시간 개별 집계
 *  - /sys/fs/cgroup/memory/memory.limit_in_bytes: 메모리 제한 값
 *  - /sys/fs/cgroup/blkio/io_serviced_recursive: I/O 횟수(건수)
 *  - /proc/net/dev의 패킷, 에러(오류) 등 상세 네트워크 이벤트
 *  - "/proc/self/cgroup" 등으로 현재 자신이 속한 cgroup 파악 가능
 *
 * └─────────────────────────────────────────────────────────────┘
 */


//컨테이너 내부에서 cgroup 파일을 직접 읽어 CPU, 메모리, 디스크, 네트워크 등 리소스 사용량을 수집하는 클래스
public class ContainerResourceMonitor {
    private static final Logger logger = Logger.getLogger(ContainerResourceMonitor.class.getName());

    private static final String CG_CPUACCT_USAGE_V1 = "/sys/fs/cgroup/cpuacct/cpuacct.usage";
    private static final String CG_CPU_STAT_V2 = "/sys/fs/cgroup/cpu.stat";
    private static final String CG_MEM_USAGE_V1 = "/sys/fs/cgroup/memory/memory.usage_in_bytes";
    private static final String CG_MEM_USAGE_V2 = "/sys/fs/cgroup/memory.current";
    private static final String CG_BLKIO_V1 = "/sys/fs/cgroup/blkio/io_service_bytes_recursive";
    private static final String CG_IO_STAT_V2 = "/sys/fs/cgroup/io.stat";
    private static final String PROC_NET_DEV = "/proc/net/dev";


    //주어진 파일 경로의 텍스트를 읽어 반환
    public static String readFile(String path) {
        try {
            //파일 내용(문자열) Return -> path : 파일 경로
            return new String(Files.readAllBytes(Paths.get(path))).trim();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to read file: " + path, e);
            return null;//실패시 null
        }
    }
    //주어진 파일 경로의 내용을 long 타입으로 파싱해서 반환
    public static Long readLongFromFile(String path) {
        //성공하면 long값, 실패하면 null값 반환
        String content = readFile(path);
        if (content == null) return null;
        try {
            return Long.parseLong(content);
        } catch (NumberFormatException e) {
            logger.log(Level.SEVERE, "Failed to parse long from file: " + path + " (content: " + content + ")", e);
            return null;
        }
    }

    // 네트워크 인터페이스별 누적 수신/송신 바이트 수를 반환
    public static Map<String, Long[]> getNetworkStats() {
        Map<String, Long[]> networkStats = new HashMap<>();
        String netDev = readFile(PROC_NET_DEV);
        if (netDev == null) return networkStats;
        String[] lines = netDev.split("\n");
        //첫 2줄은 헤더여서 2번째 줄부터 파싱
        for (int i = 2; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split(":");
            if (parts.length < 2) continue;
            String iface = parts[0].trim();
            String[] data = parts[1].trim().split("\\s+");
            if (data.length < 16) continue;
            try {
                long bytesReceived = Long.parseLong(data[0]);//수신 바이트
                long bytesSent = Long.parseLong(data[8]);//송신 바이트
                networkStats.put(iface, new Long[]{bytesReceived, bytesSent});
            } catch (NumberFormatException e) {
                logger.log(Level.WARNING, "Failed to parse network stats for interface: " + iface, e);
            }
        }
        return networkStats;
    }

    // CPU 누적 사용량(나노초) 반환 (cgroup v1/v2 모두 지원)
    public static Long getCpuUsageNano() {
        //누적 CPU 사용량을 반환
        String v1Path = CG_CPUACCT_USAGE_V1;
        if (Files.exists(Paths.get(v1Path))) {
            try {
                String content = Files.readString(Paths.get(v1Path)).trim();
                return Long.parseLong(content);
            } catch (IOException | NumberFormatException e) {
                logger.log(Level.WARNING, "Failed to read v1 cpuacct.usage", e);
            }
        } else {
            String v2Path = CG_CPU_STAT_V2;
            if (Files.exists(Paths.get(v2Path))) {
                try {
                    for (String line : Files.readAllLines(Paths.get(v2Path))) {
                        if (line.startsWith("usage_usec")) {
                            String[] parts = line.split("\\s+");
                            return Long.parseLong(parts[1]) * 1000L; // 마이크로초 → 나노초
                        }
                    }
                } catch (IOException | NumberFormatException e) {
                    logger.log(Level.WARNING, "Failed to read v2 cpu.stat", e);
                }
            }
        }
        return null;
    }

    // 컨테이너의 현재 메모리 사용량(바이트)을 반환
    public static Long getMemoryUsage() {
        Long memoryUsage = readLongFromFile(CG_MEM_USAGE_V1);
        if (memoryUsage == null) {
            memoryUsage = readLongFromFile(CG_MEM_USAGE_V2);
        }
        return memoryUsage;
    }

    // 디스크 I/O (누적) 읽기/쓰기 바이트 수 반환
    public static long[] getDiskIO() {
        long diskReadBytes = 0;
        long diskWriteBytes = 0;
        String blkioData = readFile(CG_BLKIO_V1);
        if (blkioData == null) {
            blkioData = readFile(CG_IO_STAT_V2);
            if (blkioData != null) {
                String[] lines = blkioData.split("\n");
                for (String line : lines) {
                    String[] parts = line.trim().split("\\s+");
                    for (String part : parts) {
                        if (part.startsWith("rbytes=")) {
                            try {
                                diskReadBytes += Long.parseLong(part.substring(7));
                            } catch (NumberFormatException e) {
                                logger.log(Level.WARNING, "Failed to parse rbytes: " + part, e);
                            }
                        } else if (part.startsWith("wbytes=")) {
                            try {
                                diskWriteBytes += Long.parseLong(part.substring(7));
                            } catch (NumberFormatException e) {
                                logger.log(Level.WARNING, "Failed to parse wbytes: " + part, e);
                            }
                        }
                    }
                }
            }
        } else {
            String[] lines = blkioData.split("\n");
            for (String line : lines) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length == 3) {
                    String op = parts[1];
                    try {
                        long value = Long.parseLong(parts[2]);
                        if ("Read".equalsIgnoreCase(op)) {
                            diskReadBytes += value;
                        } else if ("Write".equalsIgnoreCase(op)) {
                            diskWriteBytes += value;
                        }
                    } catch (NumberFormatException e) {
                        logger.log(Level.WARNING, "Failed to parse blkio value: " + line, e);
                    }
                }
            }
        }
        return new long[]{diskReadBytes, diskWriteBytes};
    }

    // 리소스 누적값을 모두 Map으로 반환 (변화량 계산은 외부 while문에서)
    public static Map<String, Object> collectContainerResourceRaw() {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "container");//타입
        //컨테이너 ID
        try {
            map.put("containerId", java.net.InetAddress.getLocalHost().getHostName());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to get containerId (hostname)", e);
            map.put("containerId", "unknown");
        }
        //누적 CPU 사용량(나노초)
        map.put("cpuUsageNano", getCpuUsageNano());
        //현재 메모리 사용량(바이트)
        map.put("memoryUsedBytes", getMemoryUsage());
        long[] diskIO = getDiskIO();
        map.put("diskReadBytes", diskIO[0]);//누적 디스크 읽기 바이트
        map.put("diskWriteBytes", diskIO[1]);//누적 디스크 쓰기 바이트
        // 네트워크: iface별 [받은 바이트, 보낸 바이트]
        map.put("network", getNetworkStats());//네트워크 인터페이스별 누적 바이트
        return map;
    }
}
