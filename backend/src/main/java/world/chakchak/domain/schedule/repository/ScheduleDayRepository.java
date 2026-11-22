package world.chakchak.domain.schedule.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import world.chakchak.domain.schedule.entity.ScheduleDay;

public interface ScheduleDayRepository extends JpaRepository<ScheduleDay, Long> {

    // 방의 날 목록, 1일차부터 (B-01)
    List<ScheduleDay> findAllByRoom_IdOrderByDayIndexAsc(Long roomId);

    // TODO id + 방으로 날 하나 조회 - 다른 방의 날이면 SCHEDULE_DAY_NOT_FOUND (B-02)
}
