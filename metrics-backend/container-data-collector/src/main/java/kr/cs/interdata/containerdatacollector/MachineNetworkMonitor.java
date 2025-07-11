package kr.cs.interdata.containerdatacollector;

import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MachineNetworkMonitor {
    private final List<NetworkIF> networkIFS;

    public MachineNetworkMonitor() {
        SystemInfo si = new SystemInfo();
        HardwareAbstractionLayer hal = si.getHardware();
        this.networkIFS = hal.getNetworkIFs();
    }

    public Map<String, Object> getNetworkInfoJson() {
        Map<String, Object> result = new LinkedHashMap<>();
        int idx = 0;

        for (NetworkIF net : networkIFS) {
            net.updateAttributes();

            // 활성화된 인터페이스만 포함
            if (net.getIfOperStatus() == NetworkIF.IfOperStatus.UP) {
                Map<String, Object> netInfo = new LinkedHashMap<>();
                netInfo.put("speedBps", net.getSpeed()); // bps
                netInfo.put("bytesReceived", net.getBytesRecv()); // byte
                netInfo.put("bytesSent", net.getBytesSent()); // byte
                result.put(net.getName() + "_" + idx, netInfo);
                idx++;
            }
        }

        return result;
    }
}
