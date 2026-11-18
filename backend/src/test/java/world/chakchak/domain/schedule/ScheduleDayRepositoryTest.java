package world.chakchak.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.schedule.entity.ScheduleDay;
import world.chakchak.domain.schedule.repository.ScheduleDayRepository;

/** schedule_day 테이블 저장·조회 테스트. Testcontainers의 MySQL에 V14 마이그레이션이 적용된 상태로 돈다. */
@IntegrationTest
class ScheduleDayRepositoryTest {

    @Autowired private ScheduleDayRepository scheduleDayRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private Clock clock;

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("날을 저장한 뒤 ID로 조회하면 방, 일차, 버전이 유지된다")
            void savesAndFindsDay() {
                Room room = saveRoom();
                ScheduleDay saved = scheduleDayRepository.saveAndFlush(ScheduleDay.create(room, 1));

                ScheduleDay found = scheduleDayRepository.findById(saved.getId()).orElseThrow();

                assertThat(found.getRoom().getId()).isEqualTo(room.getId());
                assertThat(found.getDayIndex()).isEqualTo(1);
                assertThat(found.getVersion()).isZero();
                assertThat(found.getCreatedAt()).isNotNull();
            }

            @Test
            @DisplayName("올린 버전을 저장하면 다시 조회해도 올린 값이 유지된다")
            void persistsIncreasedVersion() {
                ScheduleDay day =
                        scheduleDayRepository.saveAndFlush(ScheduleDay.create(saveRoom(), 1));
                day.increaseVersion();
                scheduleDayRepository.saveAndFlush(day);

                ScheduleDay found = scheduleDayRepository.findById(day.getId()).orElseThrow();

                assertThat(found.getVersion()).isEqualTo(1);
            }

            @Test
            @DisplayName("같은 일차라도 다른 방에는 각각 저장할 수 있다")
            void allowsSameDayIndexInDifferentRooms() {
                scheduleDayRepository.saveAndFlush(ScheduleDay.create(saveRoom(), 1));

                ScheduleDay other =
                        scheduleDayRepository.saveAndFlush(ScheduleDay.create(saveRoom(), 1));

                assertThat(other.getId()).isNotNull();
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("같은 방에 같은 일차를 두 번 저장하면 UNIQUE 제약 위반이 난다")
            void rejectsDuplicateDayIndexInSameRoom() {
                Room room = saveRoom();
                scheduleDayRepository.saveAndFlush(ScheduleDay.create(room, 1));

                assertThatThrownBy(() ->
                                scheduleDayRepository.saveAndFlush(ScheduleDay.create(room, 1)))
                        .isInstanceOf(DataIntegrityViolationException.class);
            }
        }
    }

    private Room saveRoom() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return roomRepository.saveAndFlush(
                Room.create(
                        "여행 " + suffix,
                        "부산",
                        LocalDate.of(2027, 7, 1),
                        LocalDate.of(2027, 7, 3),
                        suffix,
                        RoomStatus.PLANNING,
                        clock));
    }
}
