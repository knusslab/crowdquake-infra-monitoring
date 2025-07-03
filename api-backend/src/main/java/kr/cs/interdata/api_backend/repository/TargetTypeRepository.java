package kr.cs.interdata.api_backend.repository;

import kr.cs.interdata.api_backend.entity.TargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TargetTypeRepository extends JpaRepository<TargetType, Integer> {

    /**
     *  - 주어진 타입(type)에 해당하는 TargetType 엔티티를 조회한다.
     *
     * @param type  조회할 대상의 타입
     * @return  해당 타입의 TargetType 엔티티(또는 데이터)가 존재하면 Optional으로 반환, 없으면 Optional.empty()
     */
    Optional<TargetType> findByType(String type);
}
