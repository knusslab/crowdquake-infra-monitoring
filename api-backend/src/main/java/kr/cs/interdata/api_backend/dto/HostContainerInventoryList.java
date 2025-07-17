package kr.cs.interdata.api_backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * [HostContainerInventoryList]
 * - 모든 호스트와 컨테이너의 목록을 응답할 때 사용되는 DTO입니다.
 * - 각각의 Host, Container 정보를 리스트 형태로 담아 클라이언트에 전달합니다.
 */
@Setter
@Getter
public class HostContainerInventoryList {
    /**
     * Host 정보 목록
     * - HostDTO 객체의 리스트로, 시스템 내 모든 호스트의 기본 정보를 담고 있습니다.
     */
    private List<HostDTO> host;
    /**
     * Container 정보 목록
     * - ContainerDTO 객체의 리스트로, 시스템 내 모든 컨테이너의 기본 정보를 담고 있습니다.
     */
    private List<ContainerDTO> container;


    /**
     * [HostDTO]
     * - 한 대의 호스트 정보를 담는 내부 static 클래스입니다.
     */
    @Setter
    @Getter
    public static class HostDTO {
        private String id;   // 호스트의 고유 식별자(ID)
        private String name; // 호스트의 이름
    }

    /**
     * [ContainerDTO]
     * - 한 개의 컨테이너 정보를 담는 내부 static 클래스입니다.
     */
    @Setter
    @Getter
    public static class ContainerDTO {
        private String host; // 컨테이너가 소속된 호스트의 이름(또는 ID)
        private String id;   // 컨테이너의 고유 식별자(ID)
        private String name; // 컨테이너의 이름
    }
}


