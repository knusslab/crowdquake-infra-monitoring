package kr.cs.interdata.api_backend.service.repository_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.cs.interdata.api_backend.repository.ContainerInventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ContainerInventoryService {

    @Autowired
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ContainerInventoryRepository containerInventoryRepository;
    private final Logger logger = LoggerFactory.getLogger(ContainerInventoryService.class);

    @Autowired
    public ContainerInventoryService(
                                   ContainerInventoryRepository containerInventoryRepository) {
        this.containerInventoryRepository = containerInventoryRepository;
    }

    /**
     *  - containerId와 containerName 조합을 기준으로 종속되어 있는 hostName을 반환한다.
     *
     * @param containerId       container ID
     * @param containerName     container name
     * @return  해당 id와 name조합이 종속되어 있는 hostName
     */
    public String addHostNameByContainerIdAndContainerName(String containerId, String containerName) {

        return String.valueOf(containerInventoryRepository.findHostNameByContainerIdAndContainerName(containerId, containerName));
    }
}
