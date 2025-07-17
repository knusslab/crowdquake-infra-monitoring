package kr.cs.interdata.datacollector;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.file.DirectoryStream;
import java.util.ArrayList;

//리눅스 시스템의 자원 상태를 proc,sys 등의 시스템 파일을 통해 직접 읽어와서 JSON 형태로 반환
public class MachineResourceMonitor {
    private static final String HOST_ID_FILE = getDefaultHostIdPath(); //고유한 호스트 id를 저장하는 파일 경로
    private static final String PROC_PATH = "/host/proc"; // 호스트의 proc 디렉토리를 컨테이너 내에서 접근할 경로

    private long prevIdle = 0;
    private long prevTotal = 0;


    private static final String SYS_THERMAL_PATH = "/host/sys/class/thermal";
    private static final String SYS_HWMON_PATH = "/host/sys/class/hwmon";

    //운영체제별 호스트 ID 파일 경로 반환
    //윈도우 안쓰니까 리눅스만 해도 될 듯
    private static String getDefaultHostIdPath() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {//윈도우
            return System.getProperty("java.io.tmpdir") + "host-unique-id.txt";
        } else {//리눅스
            return "/tmp/host-unique-id.txt";
        }
    }

    public MachineResourceMonitor() {
        //초기화 시 CPU 사용률 계산을 위한 이전 시점의 idle/total 값을 저장해둠
        try {
            long[] cpuTimes = readCpuTimes();
            prevIdle = cpuTimes[0];
            prevTotal = cpuTimes[1];
        } catch (IOException e) {
            prevIdle = 0;
            prevTotal = 0;
        }
    }

    //proc/stats의 cpu 라인에서 idle/total 시간 누적값 읽음
    private long[] readCpuTimes() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(PROC_PATH + "/stat"));
        for (String line : lines) {
            if (line.startsWith("cpu ")) {
                String[] parts = line.trim().split("\\s+");
                long user = Long.parseLong(parts[1]);//user: 사용자 프로세스가 사용한 시간
                long nice = Long.parseLong(parts[2]);//nice: nice로 낮춰진 우선순위 프로세스가 사용한 시간
                long system = Long.parseLong(parts[3]);//system: 커널이 사용한 시간
                long idle = Long.parseLong(parts[4]);//idle: CPU가 놀고 있었던 시간
                long iowait = Long.parseLong(parts[5]);//iowait: I/O를 기다리며 idle 상태였던 시간
                long irq = Long.parseLong(parts[6]);//irq: 하드웨어 인터럽트 처리 시간
                long softirq = Long.parseLong(parts[7]);//softirq: 소프트웨어 인터럽트 처리 시간
                long steal = Long.parseLong(parts[8]);//steal: 가상화 환경에서 cpu가 할당되지 못해 기다린 시간
                long total = user + nice + system + idle + iowait + irq + softirq + steal;
                return new long[]{idle, total};
            }
        }
        throw new IOException("cpu line not found in /proc/stat");
    }

    //cpu 사용률 계산: (전체 시간 변화량 - idle  변화량)/전체 시간 변화량*100
    public double getCpuUsagePercent() {
        //proc/stat의 cpu 라인을 읽어와서 현재와 이전의 idle, total 값을 비교해 cpu 사용률을 계산
        //idle: cpu가 아무 일도 안한 시간
        try {
            long[] cpuTimes = readCpuTimes();
            long idle = cpuTimes[0];
            long total = cpuTimes[1];

            long idleDiff = idle - prevIdle;
            long totalDiff = total - prevTotal;

            prevIdle = idle;
            prevTotal = total;

            if (totalDiff == 0) return 0.0;

            double usage = (double)(totalDiff - idleDiff) / totalDiff;
            return usage * 100.0;
        } catch (IOException e) {
            return 0.0;
        }
    }

    //리눅스 호스트 시스템의 온도 센서 데이터를 읽어와 센서 이름과 측정값(섭씨 온도)를 Map<stting,Double> 형태로 반환함
    //1. /sys/class/thermal/thermal_zone*/temp (thermal_zone 방식)
    //2. /sys/class/hwmon/hwmon*/tempN_input   (하드웨어 모니터링 칩 방식)
    // 3. /proc/acpi/thermal_zone/*/temperature (ACPI 구형 시스템)
    // 결과 Map 반환 (각 센서 이름 : °C 값)
    public Map<String, Double> getHostTemperatureMap() {
        Map<String, Double> tempMap = new LinkedHashMap<>();//센서 이름과 온도 값을 저장

        // 1. thermal_zone 방식
        try (DirectoryStream<Path> zones = Files.newDirectoryStream(Paths.get("/host/sys/class/thermal"), "thermal_zone*")) {
            for (Path zone : zones) {
                String zoneName = zone.getFileName().toString();  // thermal_zone0

                //센서 타입 정보
                String type = "unknown";
                Path typePath = zone.resolve("type");
                if (Files.exists(typePath)) {
                    try {
                        type = Files.readString(typePath).trim();
                    } catch (IOException ignore) {}
                }

                //실제 온도 값 들어가 있음
                Path tempFile = zone.resolve("temp");
                if (Files.exists(tempFile)) {
                    try {
                        String raw = Files.readString(tempFile).trim();
                        double tempC = Double.parseDouble(raw) / 1000.0; //m°C(milli-Celsius) 단위로 들어와서 1000으로 나눠줌
                        tempMap.put(type + " (" + zoneName + ")", tempC);
                    } catch (Exception ignore) {}
                }
            }
        } catch (Exception ignore) {}

        // 2. hwmon 방식
        try (DirectoryStream<Path> hwmons = Files.newDirectoryStream(Paths.get("/host/sys/class/hwmon"))) {
            for (Path hwmon : hwmons) {
                //센서 장치 이름
                String name = "hwmon";
                Path namePath = hwmon.resolve("name");
                if (Files.exists(namePath)) {
                    try {
                        name = Files.readString(namePath).trim();
                    } catch (IOException ignore) {}
                }

                // 최대 temp1_input에서 temp5_input 까지 시도
                for (int i = 1; i <= 5; i++) {
                    Path tempFile = hwmon.resolve("temp" + i + "_input");
                    Path labelFile = hwmon.resolve("temp" + i + "_label");
                    String label = "temp" + i;
                    // tempN_label 파일이 있다면 센서 라벨 읽기
                    if (Files.exists(labelFile)) {
                        try {
                            label = Files.readString(labelFile).trim();
                        } catch (IOException ignore) {}
                    }

                    // 온도 데이터 읽기
                    if (Files.exists(tempFile)) {
                        try {
                            String raw = Files.readString(tempFile).trim();
                            double tempC = Double.parseDouble(raw) / 1000.0; //m°C(milli-Celsius) 단위로 들어와서 1000으로 나눠줌
                            tempMap.put(name + "/" + label, tempC);
                        } catch (Exception ignore) {}
                    }
                }
            }
        } catch (Exception ignore) {}

        // 3. ACPI 구형 방식
        try (DirectoryStream<Path> zones = Files.newDirectoryStream(Paths.get("/host/proc/acpi/thermal_zone"))) {
            for (Path zone : zones) {
                String zoneName = zone.getFileName().toString();
                Path tempFile = zone.resolve("temperature");
                if (Files.exists(tempFile)) {
                    try {
                        String raw = Files.readString(tempFile).trim();
                        //문자열 파싱해서 숫자만 추출
                        String[] parts = raw.split("\\s+");
                        for (String part : parts) {
                            try {
                                double val = Double.parseDouble(part);//숫자면 온도로 인식
                                tempMap.put("acpi (" + zoneName + ")", val);
                                break;
                            } catch (NumberFormatException ignore) {}
                        }
                    } catch (Exception ignore) {}
                }
            }
        } catch (Exception ignore) {}

        return tempMap;
    }

    //proc/meminfo에서 MemTotal, MemAvailable 값을 읽어 메모리 상태 계산
    public long getTotalMemoryBytes() {

        try {
            List<String> lines = Files.readAllLines(Paths.get(PROC_PATH + "/meminfo"));
            for (String line : lines) {
                if (line.startsWith("MemTotal:")) {
                    String[] parts = line.split("\\s+");
                    long kb = Long.parseLong(parts[1]);
                    return kb * 1024;
                }
            }
        } catch (IOException e) {}
        return 0;
    }

    public long getAvailableMemoryBytes() {
        try {
            List<String> lines = Files.readAllLines(Paths.get(PROC_PATH + "/meminfo"));
            for (String line : lines) {
                if (line.startsWith("MemAvailable:")) {
                    String[] parts = line.split("\\s+");
                    long kb = Long.parseLong(parts[1]);
                    return kb * 1024;
                }
            }
        } catch (IOException e) {}
        return 0;
    }

    public long getUsedMemoryBytes() {
        long total = getTotalMemoryBytes();
        long avail = getAvailableMemoryBytes();
        return total - avail;
    }

    // proc/mounts에서 마운트된 경로를 읽고, 각 파일시스템의 용량을 Files.getFileStore()로 조회
    public long getTotalDiskBytes() {
        long total = 0;
        try {
            List<String> lines = Files.readAllLines(Paths.get(PROC_PATH + "/mounts"));
            for (String line : lines) {
                String[] parts = line.split(" ");
                if (parts.length > 1) {
                    String mountPoint = parts[1];
                    Path path = Paths.get(mountPoint);
                    if (Files.exists(path)) {
                        try {
                            total += Files.getFileStore(path).getTotalSpace();
                        } catch (IOException ignored) {}
                    }
                }
            }
        } catch (IOException e) {}
        return total;
    }

    public long getFreeDiskBytes() {
        long free = 0;
        try {
            List<String> lines = Files.readAllLines(Paths.get(PROC_PATH + "/mounts"));
            for (String line : lines) {
                String[] parts = line.split(" ");
                if (parts.length > 1) {
                    String mountPoint = parts[1];
                    Path path = Paths.get(mountPoint);
                    if (Files.exists(path)) {
                        try {
                            free += Files.getFileStore(path).getUsableSpace();
                        } catch (IOException ignored) {}
                    }
                }
            }
        } catch (IOException e) {}
        return free;
    }

    public long getUsedDiskBytes() {
        return getTotalDiskBytes() - getFreeDiskBytes();
    }

    //proc/diskstats에서 각 디스크 장치의 읽기/쓰기 섹처 수를 읽어 바이트로 변환함
    //섹터 수*512= 바이트 수
    public long getDiskReadBytes() {
        long totalReadBytes = 0;
        try {
            List<String> lines = Files.readAllLines(Paths.get(PROC_PATH + "/diskstats"));
            for (String line : lines) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 6) {
                    long sectorsRead = Long.parseLong(parts[5]);
                    totalReadBytes += sectorsRead * 512;
                }
            }
        } catch (IOException e) {}
        return totalReadBytes;
    }

    public long getDiskWriteBytes() {
        long totalWriteBytes = 0;
        try {
            List<String> lines = Files.readAllLines(Paths.get(PROC_PATH + "/diskstats"));
            for (String line : lines) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 10) {
                    long sectorsWritten = Long.parseLong(parts[9]);
                    totalWriteBytes += sectorsWritten * 512;
                }
            }
        } catch (IOException e) {}
        return totalWriteBytes;
    }

    //proc/diskstats에서 각 디스크 장치의 읽기 및 쓰기 횟수(누적) 합산
    public long getDiskReadCount() {
        long totalReads = 0;
        try {
            List<String> lines = Files.readAllLines(Paths.get(PROC_PATH + "/diskstats"));
            for (String line : lines) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 4) {
                    long reads = Long.parseLong(parts[3]);
                    totalReads += reads;
                }
            }
        } catch (IOException e) {}
        return totalReads;
    }

    public long getDiskWriteCount() {
        long totalWrites = 0;
        try {
            List<String> lines = Files.readAllLines(Paths.get(PROC_PATH + "/diskstats"));
            for (String line : lines) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 8) {
                    long writes = Long.parseLong(parts[7]);
                    totalWrites += writes;
                }
            }
        } catch (IOException e) {}
        return totalWrites;
    }

    private String getOrCreateHostId() {
        //tmp/host-unique-id.txt 파일이 있으면 읽고, 없으면 UUID를 생성해서 저장
        try {
            Path path = Paths.get(HOST_ID_FILE);
            if (Files.exists(path)) {
                return Files.readString(path).trim();
            } else {
                String uuid = UUID.randomUUID().toString();
                Files.writeString(path, uuid, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                return uuid;
            }
        } catch (IOException e) {
            return UUID.randomUUID().toString();
        }
    }

    public String getResourcesAsJson() {
        MachineNetworkMonitor networkMonitor = new MachineNetworkMonitor();
        Map<String, Object> jsonMap = new LinkedHashMap<>();
        jsonMap.put("type", "host");
        jsonMap.put("hostId", getOrCreateHostId());
        jsonMap.put("cpuUsagePercent", getCpuUsagePercent());
        jsonMap.put("memoryTotalBytes", getTotalMemoryBytes());
        jsonMap.put("memoryUsedBytes", getUsedMemoryBytes());
        jsonMap.put("memoryFreeBytes", getAvailableMemoryBytes());
        jsonMap.put("diskTotalBytes", getTotalDiskBytes());
        jsonMap.put("diskUsedBytes", getUsedDiskBytes());
        jsonMap.put("diskFreeBytes", getFreeDiskBytes());
        jsonMap.put("diskReadBytes", getDiskReadBytes());
        jsonMap.put("diskWriteBytes", getDiskWriteBytes());
        jsonMap.put("network", networkMonitor.getNetworkInfoJson());
        jsonMap.put("temperatures", getHostTemperatureMap());
        return new Gson().toJson(jsonMap);
    }
}
