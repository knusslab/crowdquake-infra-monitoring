package kr.cs.interdata.api_backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class HostContainerInventoryList {
    private List<HostDTO> host;
    private List<ContainerDTO> container;

    @Setter
    @Getter
    public static class HostDTO {
        private String id;
        private String name;
    }

    @Setter
    @Getter
    public static class ContainerDTO {
        private String host;
        private String id;
        private String name;
    }
}


