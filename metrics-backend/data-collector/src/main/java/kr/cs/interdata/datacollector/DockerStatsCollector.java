package kr.cs.interdata.datacollector;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
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

import static java.lang.System.exit;

public class DockerStatsCollector {

    private static final Logger logger = LoggerFactory.getLogger(DockerStatsCollector.class);

    private DockerClient dockerClient = null;

    public DockerStatsCollector() {
        this.dockerClient = initDockerClientWithFallback();
    }

    private DockerClient initDockerClientWithFallback() {
        String defaultHost = "unix:///var/run/docker.sock";  // 기본값은 Linux 방식

        try {
            dockerClient = createDockerClient(defaultHost);
            // 테스트로 간단한 명령 하나 실행
            dockerClient.pingCmd().exec();  // 실패 시 바로 catch 블록으로 이동
            return dockerClient;

        } catch (Exception ex) {
            logger.warn("기본 Unix 소켓 연결 실패: {}", ex.getMessage());
            logger.warn("Windows 환경으로 재시도합니다...");

            try {
                // 2. 윈도우 환경 (npipe 방식: 안전)
                String windowsNpipe = "npipe:////./pipe/docker_engine";
                dockerClient = createDockerClient(windowsNpipe);
                logger.info("성공적인 create docker client...");
                dockerClient.infoCmd().exec();
                logger.info("infoCmd 성공");
                dockerClient.pingCmd().exec();
                logger.info("Windows <UNK> <UNK>");
                return dockerClient;


            } catch (Exception e) {
                logger.error("Windows Docker 연결도 실패했습니다: {}" , e.getMessage());
                logger.error("Docker 데몬에 연결할 수 없습니다.", e);
                exit(1);
                return null;
            }
        }
    }

    private DockerClient createDockerClient(String dockerHost) {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();

        ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }


    public Map<String, Statistics> getAllContainerStats() {
        Map<String, Statistics> statsMap = new HashMap<>();

        try {
            List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();

            for (Container container : containers) {
                String containerId = container.getId();

                Statistics stats = getContainerStatsOnce(containerId);
                if (stats != null) {
                    statsMap.put(containerId, stats);
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

        StatsCallback callback = new StatsCallback(latch, latest);

        try {
            dockerClient.statsCmd(containerId).exec(callback);

            boolean success = latch.await(3, TimeUnit.SECONDS);
            return success ? latest[0] : null;

        } catch (Exception e) {
            logger.error("Stats 수집 실패: {}", e.getMessage());
            return null;
        }
    }

    public class StatsCallback extends ResultCallback.Adapter<Statistics> {
        private final CountDownLatch latch;
        private final Statistics[] latest;

        public StatsCallback(CountDownLatch latch, Statistics[] latest) {
            this.latch = latch;
            this.latest = latest;
        }

        @Override
        public void onNext(Statistics stats) {
            latest[0] = stats;
            latch.countDown();
        }

        @Override
        public void onError(Throwable throwable) {
            System.err.println("Stats 수집 중 오류: " + throwable.getMessage());
            latch.countDown();
        }

        @Override
        public void onComplete() {
            try {
                this.close(); // 안전한 위치에서 닫기
            } catch (IOException e) {
                logger.warn("StatsCallback close 중 오류", e);
            }
        }
    }


}

