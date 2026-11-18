package world.chakchak.domain.schedule.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import world.chakchak.domain.schedule.entity.ScheduleDay;

public interface ScheduleDayRepository extends JpaRepository<ScheduleDay, Long> {

    // TODO 방의 날 목록을 dayIndex 순으로 (B-01)
    // TODO id + 방으로 날 하나 조회 - 다른 방의 날이면 SCHEDULE_DAY_NOT_FOUND (B-02)
}
