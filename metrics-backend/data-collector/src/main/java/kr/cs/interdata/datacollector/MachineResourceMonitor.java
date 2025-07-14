//package kr.cs.interdata.datacollector;
//

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

public class MachineResourceMonitor {
    private static final String HOST_ID_FILE = getDefaultHostIdPath();
    private static final String PROC_PATH = "/host/proc";

    private long prevIdle = 0;
    private long prevTotal = 0;

    private static String getDefaultHostIdPath() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return System.getProperty("java.io.tmpdir") + "host-unique-id.txt";
        } else {
            return "/tmp/host-unique-id.txt";
        }
    }

    public MachineResourceMonitor() {
        try {
            long[] cpuTimes = readCpuTimes();
            prevIdle = cpuTimes[0];
            prevTotal = cpuTimes[1];
        } catch (IOException e) {
            prevIdle = 0;
            prevTotal = 0;
        }
    }

    private long[] readCpuTimes() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(PROC_PATH + "/stat"));
        for (String line : lines) {
            if (line.startsWith("cpu ")) {
                String[] parts = line.trim().split("\\s+");
                long user = Long.parseLong(parts[1]);
                long nice = Long.parseLong(parts[2]);
                long system = Long.parseLong(parts[3]);
                long idle = Long.parseLong(parts[4]);
                long iowait = Long.parseLong(parts[5]);
                long irq = Long.parseLong(parts[6]);
                long softirq = Long.parseLong(parts[7]);
                long steal = Long.parseLong(parts[8]);
                long total = user + nice + system + idle + iowait + irq + softirq + steal;
                return new long[]{idle, total};
            }
        }
        throw new IOException("cpu line not found in /proc/stat");
    }

    public double getCpuUsagePercent() {
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
        return new Gson().toJson(jsonMap);
    }
}

//import com.google.gson.Gson;
//import oshi.SystemInfo;
//import oshi.hardware.CentralProcessor;
//import oshi.hardware.GlobalMemory;
//import oshi.hardware.HWDiskStore;
//import oshi.software.os.OSFileStore;
//import oshi.software.os.OperatingSystem;
//
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.nio.file.StandardOpenOption;
//import java.util.LinkedHashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.UUID;
//
//public class MachineResourceMonitor {
//    private final SystemInfo systemInfo;// 시스템 전체 정보를 제공하는 객체
//    private final CentralProcessor processor;//cpu관련
//    private final GlobalMemory memory;//메모리 관련 정보
//    private final OperatingSystem os;//운영체제 정보
//    private long[] prevTicks;//이전 cpu 사용 상태를 저장->cpu 사용률 계산에 씀
//    private static final String HOST_ID_FILE = getDefaultHostIdPath();//호스트 ID를 저장하는 파일
//
//    private static String getDefaultHostIdPath() {
//        //os에 따라 호스트 고유 id 저장할 파일 경로를 다르게 설정하기 위해 만들어놓긴 함.
//        String osName = System.getProperty("os.name").toLowerCase();
//        if (osName.contains("win")) {//window
//            return System.getProperty("java.io.tmpdir") + "host-unique-id.txt"; // 예: C:\Users\xxx\AppData\Local\Temp\
//        } else {
//            return "/tmp/host-unique-id.txt";
//        }
//    }
//    public MachineResourceMonitor() {
//        //생성자
//        //시스템의 하드웨어 정보들 초기화
//        systemInfo = new SystemInfo();
//        processor = systemInfo.getHardware().getProcessor();
//        memory = systemInfo.getHardware().getMemory();
//        os = systemInfo.getOperatingSystem();
//        prevTicks = processor.getSystemCpuLoadTicks();//초기 cpu 상태 저장
//        //this.networkIFS = systemInfo.getHardware().getNetworkIFs();
//    }
//
//    public double getCpuUsagePercent() {
//        //cpu 사용률 계산
//        long[] currentTicks = processor.getSystemCpuLoadTicks();
//        double load = processor.getSystemCpuLoadBetweenTicks(prevTicks);
//        prevTicks = currentTicks;//다음 계산하기 위해서 현재 상태 저장해야함.
//        return load * 100.0;
//    }
//
//    public long getTotalMemoryBytes() {
//        //총 메모리 크기(byte)
//        return memory.getTotal();
//    }
//
//    public long getAvailableMemoryBytes() {
//        //남은? 사용 가능한 메모리 크기(byte)
//        return memory.getAvailable();
//    }
//
//    public long getUsedMemoryBytes() {
//        //사용 중인 메모리 크기
//        return getTotalMemoryBytes() - getAvailableMemoryBytes();//총 메모리 - 사용 가능한 메모리
//    }
//
//    public long getTotalDiskBytes() {
//        //총 디스크 용량 -> 모든 디스크 합침(byte)
//        long total = 0;
//        for (OSFileStore fs : os.getFileSystem().getFileStores()) {
//            total += fs.getTotalSpace();
//        }
//        return total;
//    }
//
//    public long getFreeDiskBytes() {
//        //사용 가능한 디스크 용량
//        long free = 0;
//        for (OSFileStore fs : os.getFileSystem().getFileStores()) {
//            free += fs.getUsableSpace();
//        }
//        return free;
//    }
//
//    public long getUsedDiskBytes() {
//        //사용중인 디스크 용량
//        return getTotalDiskBytes() - getFreeDiskBytes();//총 용량 - 남은 용량
//    }
//
//    public long getDiskReadCount() {
//        //disk 읽은 수 -> 나중에 필요하면 써야지..일단 들고와봄
//        long totalReads = 0;
//        List<HWDiskStore> diskStores = systemInfo.getHardware().getDiskStores();
//        for (HWDiskStore disk : diskStores) {
//            disk.updateAttributes();
//            totalReads += disk.getReads();
//        }
//        return totalReads;
//    }
//    public long getDiskReadBytes() {
//        // 디스크에서 읽은 총 바이트 수
//        long totalReadBytes = 0;
//        List<HWDiskStore> diskStores = systemInfo.getHardware().getDiskStores();
//        for (HWDiskStore disk : diskStores) {
//            disk.updateAttributes(); // 디스크 상태 최신화
//            totalReadBytes += disk.getReadBytes(); // 읽은 바이트 수 누적
//        }
//        return totalReadBytes;
//    }
//
//    public long getDiskWriteBytes() {
//        // 디스크에 쓴 총 바이트 수
//        long totalWriteBytes = 0;
//        List<HWDiskStore> diskStores = systemInfo.getHardware().getDiskStores();
//        for (HWDiskStore disk : diskStores) {
//            disk.updateAttributes(); // 디스크 상태 최신화
//            totalWriteBytes += disk.getWriteBytes(); // 쓴 바이트 수 누적
//        }
//        return totalWriteBytes;
//    }
//    public long getDiskWriteCount() {
//        //disk 쓴 수 -> 나중에 필요하면 써야지..ㅎㅎㅎ일단 들고와봄
//        long totalWrites = 0;
//        List<HWDiskStore> diskStores = systemInfo.getHardware().getDiskStores();
//        for (HWDiskStore disk : diskStores) {
//            disk.updateAttributes();
//            totalWrites += disk.getWrites();
//        }
//        return totalWrites;
//    }
//
//    private String getOrCreateHostId() {
//        //이 부분 수정해야함.
//        //로컬 파일에 저장된 고유 호스트 ID를 읽거나, 없으면 새로 생성
//        //뭔가 이렇게 하면 안될거 같아서 다른 방법 찾아봐야할거 같은..
//        //일단 생각해보기
//        try {
//
//
//            Path path = Paths.get(HOST_ID_FILE);
//            if (Files.exists(path)) {
//                return Files.readString(path).trim();
//            } else {
//                String uuid = UUID.randomUUID().toString();
//                Files.writeString(path, uuid, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
//                return uuid;
//            }
//        } catch (IOException e) {
//            return UUID.randomUUID().toString(); // fallback
//        }
//    }
//
//    public String getResourcesAsJson() {
//        //모든 resource 정보를 json 형식으로 반환
//        MachineNetworkMonitor networkMonitor = new MachineNetworkMonitor();
//        Map<String, Object> jsonMap = new LinkedHashMap<>();
//        jsonMap.put("type","host");//타입 지정
//
//        jsonMap.put("hostId", getOrCreateHostId());//고유 id
//        jsonMap.put("cpuUsagePercent", getCpuUsagePercent());
//        jsonMap.put("memoryTotalBytes", getTotalMemoryBytes());
//        jsonMap.put("memoryUsedBytes", getUsedMemoryBytes());
//        jsonMap.put("memoryFreeBytes", getAvailableMemoryBytes());
//        jsonMap.put("diskTotalBytes", getTotalDiskBytes());
//        jsonMap.put("diskUsedBytes", getUsedDiskBytes());
//        jsonMap.put("diskFreeBytes", getFreeDiskBytes());
//        jsonMap.put("diskReadBytes", getDiskReadBytes());
//        jsonMap.put("diskWriteBytes", getDiskWriteBytes());
//        jsonMap.put("network", networkMonitor.getNetworkInfoJson());
//
//        return new Gson().toJson(jsonMap);//json 문자열로 변환
//    }
//}