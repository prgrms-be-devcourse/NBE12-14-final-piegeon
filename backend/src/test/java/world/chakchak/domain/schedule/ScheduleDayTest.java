package world.chakchak.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.schedule.entity.ScheduleDay;

/** ScheduleDay 엔티티 규칙 단위 테스트. Spring 없이 돈다. */
class ScheduleDayTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK =
            Clock.fixed(LocalDateTime.of(2027, 1, 1, 12, 0).atZone(SEOUL).toInstant(), SEOUL);

    private final Room room =
            Room.create(
                    "여름 여행",
                    "부산",
                    LocalDate.of(2027, 7, 1),
                    LocalDate.of(2027, 7, 3),
                    "ABCD1234",
                    RoomStatus.PLANNING,
                    CLOCK);

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("날을 만들면 전달받은 방과 일차를 보유하고 버전은 0에서 시작한다")
            void createsDay() {
                ScheduleDay day = ScheduleDay.create(room, 2);

                assertThat(day.getRoom()).isSameAs(room);
                assertThat(day.getDayIndex()).isEqualTo(2);
                assertThat(day.getVersion()).isZero();
            }
        }
    }

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("1일차의 날짜는 여행 시작일이다")
            void firstDayIsStartDate() {
                ScheduleDay day = ScheduleDay.create(room, 1);

                assertThat(day.getDate()).isEqualTo(LocalDate.of(2027, 7, 1));
            }

            @Test
            @DisplayName("N일차의 날짜는 시작일에서 N-1일 뒤다")
            void nthDayIsStartDatePlusNMinusOne() {
                ScheduleDay day = ScheduleDay.create(room, 3);

                assertThat(day.getDate()).isEqualTo(LocalDate.of(2027, 7, 3));
            }

            @Test
            @DisplayName("요청이 본 버전이 지금 버전과 같으면 같은 버전으로 판단한다")
            void sameVersion() {
                ScheduleDay day = ScheduleDay.create(room, 1);

                assertThat(day.isSameVersion(0)).isTrue();
            }

            @Test
            @DisplayName("요청이 본 버전이 지금 버전과 다르면 다른 버전으로 판단한다")
            void differentVersion() {
                ScheduleDay day = ScheduleDay.create(room, 1);
                day.increaseVersion();

                assertThat(day.isSameVersion(0)).isFalse();
            }
        }
    }

    @Nested
    class Update {

        @Nested
        class Success {

            @Test
            @DisplayName("버전을 올리면 1씩 증가한다")
            void increasesVersionByOne() {
                ScheduleDay day = ScheduleDay.create(room, 1);

                day.increaseVersion();
                day.increaseVersion();

                assertThat(day.getVersion()).isEqualTo(2);
            }
        }
    }
}
