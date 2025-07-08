// 수정된 DockerStatsCollector.java
package kr.cs.interdata.datacollector;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.transport.DockerHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;

public class DockerStatsCollector {

    private final DockerClient dockerClient;
    private final Logger logger = LoggerFactory.getLogger(DockerStatsCollector.class);

    public DockerStatsCollector() {
        String dockerHost = System.getenv("DOCKER_HOST");
        if (dockerHost == null || dockerHost.isEmpty()) {
            dockerHost = detectDockerHostByOS();
        }

        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .connectionTimeout(Duration.ofSeconds(5))
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);
    }

    private String detectDockerHostByOS() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "npipe://./pipe/docker_engine";
        } else {
            return "unix:///var/run/docker.sock";
        }
    }

    public List<Container> listAllContainers() {
        return dockerClient.listContainersCmd().withShowAll(true).exec();
    }

    // DockerClient 인스턴스를 외부에서 사용할 수 있도록 추가
    public DockerClient getDockerClient() {
        return this.dockerClient;
    }
}
