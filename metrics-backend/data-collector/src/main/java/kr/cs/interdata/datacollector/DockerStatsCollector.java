package kr.cs.interdata.datacollector;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DockerStatsCollector {

    private static final Logger logger = LoggerFactory.getLogger(DockerStatsCollector.class);

    private final DockerClient dockerClient;

    public DockerStatsCollector() {
        // 최신 문서 기준
        var config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("unix:///var/run/docker.sock")
                .build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
    }

    public Map<String, Statistics> getAllContainerStats() {
        Map<String, Statistics> statsMap = new HashMap<>();

        try {
            List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();

            for (Container container : containers) {
                Statistics stats = getContainerStatsOnce(container.getId());
                if (stats != null) {
                    // CPU, 메모리 등 추출
                }
            }
        } catch (Exception e) {
            logger.error("도커 클라이언트 오류", e);
        }

        return statsMap;
    }

    public Statistics getContainerStatsOnce(String containerId) {
        final CountDownLatch latch = new CountDownLatch(1);
        final Statistics[] latest = new Statistics[1];

        try {
            dockerClient.statsCmd(containerId).exec(new ResultCallback.Adapter<Statistics>() {
                @Override
                public void onNext(Statistics stats) {
                    latest[0] = stats;
                    latch.countDown(); // 수신되면 해제
                    try {
                        this.close(); // 명시적으로 콜백 종료
                    } catch (IOException e) {
                        logger.warn("<UNK> <UNK> <UNK>", e);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    System.err.println("Stats 수집 중 오류: " + throwable.getMessage());
                    latch.countDown();
                }
            });

            // 3초 안에 수신 못하면 null 반환
            boolean success = latch.await(3, TimeUnit.SECONDS);
            return success ? latest[0] : null;

        } catch (Exception e) {
            System.err.println("Stats 수집 실패: " + e.getMessage());
            return null;
        }
    }
}

