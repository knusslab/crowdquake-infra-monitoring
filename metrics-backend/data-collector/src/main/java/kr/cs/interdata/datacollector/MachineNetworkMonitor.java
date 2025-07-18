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

        //참고) 제일 밑에 /proc/net/dev 파일 구조와 각 필드가 의미하는 내용있습니다!

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

/*
 * ┌──────────────────────────────────── /proc/net/dev 구조 설명 ─────────────────────────────────────┐
 *
 *  # Sample (/proc/net/dev) file excerpt:
 *  Inter-|   Receive                                                |  Transmit
 *   face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
 *   eth0: 268524460 366503    0    0    0     0          0         0    25345645  348070    0    0    0     0      0          0
 *     lo:   134526     140    0    0    0     0          0         0      134526     140    0    0    0     0      0          0
 *
 *  필드 별 인덱스 및 의미 (양쪽 공백/콜론 기준 split 후):
 *   - 인덱스 0~7 : 수신(Receive) 통계
 *        [0] bytes        : 누적 수신 바이트
 *        [1] packets      : 누적 수신 패킷 수
 *        [2] errs         : 수신 오류
 *        [3] drop         : 수신 drop 패킷 수
 *        [4] fifo         : 수신 fifo 에러
 *        [5] frame        : 수신 frame 에러
 *        [6] compressed   : 수신 압축 패킷 수
 *        [7] multicast    : 수신 멀티캐스트 패킷 수
 *   - 인덱스 8~15 : 송신(Transmit) 통계
 *        [8] bytes        : 누적 송신 바이트
 *        [9] packets      : 누적 송신 패킷 수
 *       [10] errs         : 송신 오류
 *       [11] drop         : 송신 drop 패킷 수
 *       [12] fifo         : 송신 fifo 에러
 *       [13] colls        : 송신 충돌(collision) 횟수
 *       [14] carrier      : 캐리어 에러
 *       [15] compressed   : 송신 압축 패킷 수
 *
 * └─────────────────────────────────────────────────────────────────────────────────────────────┘
 */
