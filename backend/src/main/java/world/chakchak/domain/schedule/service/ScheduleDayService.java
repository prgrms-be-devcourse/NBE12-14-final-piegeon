package world.chakchak.domain.schedule.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.schedule.entity.ScheduleDay;
import world.chakchak.domain.schedule.repository.ScheduleDayRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleDayService {

    private final ScheduleDayRepository scheduleDayRepository;

    @Transactional
    public void createForTrip(Room room) {
        int tripDays =
                Math.toIntExact(
                        room.getEndDate().toEpochDay() - room.getStartDate().toEpochDay() + 1);
        for (int dayIndex = 1; dayIndex <= tripDays; dayIndex++) {
            scheduleDayRepository.save(ScheduleDay.create(room, dayIndex));
        }
    }
}
