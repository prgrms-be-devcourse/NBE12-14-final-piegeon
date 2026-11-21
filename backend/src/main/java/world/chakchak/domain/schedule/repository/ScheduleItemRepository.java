package world.chakchak.domain.schedule.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import world.chakchak.domain.schedule.entity.ScheduleItem;

public interface ScheduleItemRepository extends JpaRepository<ScheduleItem, Long> {

    // 방의 블록 전부, 순서값 순 (B-01). 장소 이름·주소도 같이 가져와서 블록마다 장소를 따로 조회하지 않는다
    @EntityGraph(attributePaths = "placeCandidate")
    List<ScheduleItem> findAllByScheduleDay_Room_IdOrderBySortOrderAsc(Long roomId);

    // TODO 날의 블록 개수 - 하루 10개 제한 (B-02)
}
