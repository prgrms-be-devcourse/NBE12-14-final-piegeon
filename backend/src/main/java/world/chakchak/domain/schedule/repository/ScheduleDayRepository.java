package world.chakchak.domain.schedule.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import world.chakchak.domain.schedule.entity.ScheduleDay;

public interface ScheduleDayRepository extends JpaRepository<ScheduleDay, Long> {

    // 방의 날 목록, 1일차부터 (B-01)
    List<ScheduleDay> findAllByRoom_IdOrderByDayIndexAsc(Long roomId);

    // 이 방의 날 하나. 다른 방의 날이면 비어 있다 (B-02)
    Optional<ScheduleDay> findByIdAndRoom_Id(Long id, Long roomId);
}
