package kr.cs.interdata.datacollector;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import lombok.Getter;

import java.time.Duration;
import java.util.*;

/**
 * DockerStatsCollector는 Docker API를 통해 컨테이너 리스트 및 리소스 정보를 수집할 수 있는 유틸리티 클래스이다.
 * OS 환경에 따라 Docker 데몬의 접속 주소를 자동으로 설정하며, 외부에서 DockerClient 인스턴스를 직접 사용할 수 있도록 제공한다.
 */
public class DockerStatsCollector {

    /**
     * 외부에서 접근 가능한 DockerClient 인스턴스
     */
    @Getter
    private final DockerClient dockerClient;

    /**
     * 생성자: OS 환경에 따라 DockerHost를 자동 설정하고 DockerClient 초기화
     */
    public DockerStatsCollector() {
        // 환경변수에서 DOCKER_HOST 값을 우선 사용
        String dockerHost = System.getenv("DOCKER_HOST");
        if (dockerHost == null || dockerHost.isEmpty()) {
            dockerHost = detectDockerHostByOS();
        }

        // Docker 클라이언트 설정 구성
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();

        // HTTP 클라이언트 구성 (타임아웃 설정 포함)
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .connectionTimeout(Duration.ofSeconds(5))
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        // DockerClient 초기화
        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
    }

    /**
     * 현재 OS에 따라 적절한 Docker Host URI 반환
     * - Windows: npipe
     * - Linux/macOS: Unix socket
     *
     * @return Docker host URI 문자열
     */
    private String detectDockerHostByOS() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "npipe://./pipe/docker_engine";
        } else {
            return "unix:///var/run/docker.sock";
        }
    }

    /**
     * 현재 시스템에서 실행 중인 모든 Docker 컨테이너 목록을 반환한다.
     *
     * @return 컨테이너 리스트 (중지된 컨테이너 포함)
     */
    public List<Container> listAllContainers() {
        return dockerClient.listContainersCmd()
                .withShowAll(true) // 중지된 컨테이너도 포함
                .exec();
    }
}
