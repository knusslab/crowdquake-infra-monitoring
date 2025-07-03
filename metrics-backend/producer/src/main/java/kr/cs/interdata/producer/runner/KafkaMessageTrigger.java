package kr.cs.interdata.producer.runner;

import com.google.gson.Gson;
import kr.cs.interdata.producer.service.KafkaProducerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Profile("!test")
@Slf4j
public class KafkaMessageTrigger implements CommandLineRunner {

    private final KafkaProducerService kafkaProducerService;

    @Autowired
    public KafkaMessageTrigger(KafkaProducerService kafkaProducerService) {
        this.kafkaProducerService = kafkaProducerService;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Run KafkaMessageTrigger");

        String jsonPayload = """
                {
                    "type": "host",
                    "hostId": "test-host-id",
                    "cpuUsagePercent": 75.0,
                     "memoryTotalBytes": 16000000000,
                     "memoryUsedBytes": 12000000000
                }
        """;

        kafkaProducerService.routeMessageBasedOnType(jsonPayload);

//        startRealTimeProcessing();
    }

//    /***
//     * 실시간 데이터 처리 : 1초마다 전송
//     */
//    public void startRealTimeProcessing() {
//        new Thread(() -> {
//            while (true) {
//                String jsonPayload = getJsonFromDataCollector();
//
//                kafkaProducerService.routeMessageBasedOnType(jsonPayload);
//
//                try {
//                    Thread.sleep(1000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//        }).start();
//    }
//
//    /***
//     * 데이터를 받아오는 로직 필요
//     * @return
//     */
//    private String getJsonFromDataCollector() {
//        // Mock Data
//        return "{ \"type\": \"host\", \"cpu\": 45, \"memory\": 1024 }";
//    }
}
