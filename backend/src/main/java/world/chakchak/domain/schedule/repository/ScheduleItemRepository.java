package world.chakchak.domain.schedule.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import world.chakchak.domain.schedule.entity.ScheduleItem;

public interface ScheduleItemRepository extends JpaRepository<ScheduleItem, Long> {

    // TODO 날의 블록 목록을 sortOrder 순으로 (B-01, 응답 스냅샷)
    // TODO 날의 블록 개수 - 하루 10개 제한 (B-02)
}
