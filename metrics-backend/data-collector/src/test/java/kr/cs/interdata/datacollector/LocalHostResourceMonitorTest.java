package kr.cs.interdata.datacollector;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

public class LocalHostResourceMonitorTest {

    @Test
    public void testGetResourcesAsJson_prettyPrint() {
        LocalHostResourceMonitor monitor = new LocalHostResourceMonitor();

        String rawJson = monitor.getResourcesAsJson();

        Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();
        Object jsonObject = prettyGson.fromJson(rawJson, Object.class);
        String prettyJson = prettyGson.toJson(jsonObject);

        System.out.println(prettyJson);
    }
}

/**package kr.cs.interdata.datacollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LocalHostResourceMonitorTest {
    private LocalHostResourceMonitor monitor;

    @BeforeEach
    public void setUp() {
        monitor = new LocalHostResourceMonitor();
    }

    @Test
    public void testCpuUsage() {
        double cpuUsage = monitor.getCpuUsagePercent();
        System.out.println("CPU Usage (%): " + cpuUsage);
        assertTrue(cpuUsage >= 0.0 && cpuUsage <= 100.0, "CPU 사용률은 0~100% 사이여야 함");
    }

    @Test
    public void testMemory() {
        long total = monitor.getTotalMemoryBytes();
        long free = monitor.getAvailableMemoryBytes();
        long used = monitor.getUsedMemoryBytes();

        System.out.println("Memory Total (bytes): " + total);
        System.out.println("Memory Free (bytes): " + free);
        System.out.println("Memory Used (bytes): " + used);

        assertTrue(total > 0, "전체 메모리는 0보다 커야 함");
        assertEquals(total, free + used, 1024 * 1024, "메모리 사용량 계산이 정확해야 함 (오차 허용)");
    }

    @Test
    public void testDisk() {
        long total = monitor.getTotalDiskBytes();
        long free = monitor.getFreeDiskBytes();
        long used = monitor.getUsedDiskBytes();

        System.out.println("Disk Total (bytes): " + total);
        System.out.println("Disk Free (bytes): " + free);
        System.out.println("Disk Used (bytes): " + used);

        assertTrue(total > 0, "디스크 전체 용량은 0보다 커야 함");
        assertEquals(total, free + used, 1024 * 1024 * 10, "디스크 사용량 계산이 정확해야 함 (오차 허용)");
    }

    @Test
    public void testDiskReadWriteCounts() {
        long reads = monitor.getDiskReadCount();
        long writes = monitor.getDiskWriteCount();

        System.out.println("Disk Reads: " + reads);
        System.out.println("Disk Writes: " + writes);

        assertTrue(reads >= 0, "읽기 횟수는 0 이상이어야 함");
        assertTrue(writes >= 0, "쓰기 횟수는 0 이상이어야 함");
    }

    @Test
    public void testGetResourcesAsJson() {
        String json = monitor.getResourcesAsJson();
        System.out.println("Resource JSON: " + json );

        assertNotNull(json, "JSON 문자열이 null이면 안 됨");
        assertTrue(json.contains("cpuUsagePercent"), "JSON에 CPU 항목이 포함되어야 함");
        assertTrue(json.contains("memoryTotalBytes"), "JSON에 메모리 항목이 포함되어야 함");
        assertTrue(json.contains("network"), "JSON에 네트워크 항목이 포함되어야 함");
    }
}**/
