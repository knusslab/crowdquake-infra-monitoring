package kr.cs.interdata.localhostdatacollector;

import oshi.SystemInfo;
import oshi.hardware.NetworkIF;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF.IfOperStatus;
import com.google.gson.Gson;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class LocalHostNetworkMonitor {
    private final List<NetworkIF> networkIFS;

    public LocalHostNetworkMonitor() {
        SystemInfo si = new SystemInfo();
        HardwareAbstractionLayer hal = si.getHardware();
        this.networkIFS = hal.getNetworkIFs();
    }

    /**
    public List<String> getMacAddresses() {
        List<String> macs = new ArrayList<>();
        for (NetworkIF net : networkIFS) {
            String mac = net.getMacaddr();
            if (mac != null && !mac.isEmpty() && !mac.equals("00:00:00:00:00:00")) {
                macs.add(mac);
            }
        }
        return macs;
    }
     **/

    public Map<String, Object> getNetworkInfoJson() {
        Map<String, Object> result = new LinkedHashMap<>();
        int idx = 0;

        for (NetworkIF net : networkIFS) {
            net.updateAttributes();

            // 활성화된 인터페이스만 포함
            if (net.getIfOperStatus() == IfOperStatus.UP) {
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
