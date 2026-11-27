package world.chakchak.domain.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.place.errorcode.PlaceErrorCode;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.schedule.entity.ScheduleDay;
import world.chakchak.domain.schedule.entity.ScheduleItem;
import world.chakchak.domain.user.entity.User;
import world.chakchak.global.error.BusinessException;

/** ScheduleItem 엔티티 규칙 단위 테스트. Spring 없이 돈다. */
class ScheduleItemTest {

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
    private final RoomMember member =
            RoomMember.create(
                    room, User.create("tester@example.com", "테스터", "hashed-password"), CLOCK);
    private final ScheduleDay day = ScheduleDay.create(room, 1);

    private PlaceCandidate newPlace() {
        return PlaceCandidate.create(
                room, "place-1", "광장시장", "market", "서울 종로구", null, null, member);
    }

    private PlaceCandidate confirmedPlace() {
        PlaceCandidate place = newPlace();
        place.confirm(CLOCK);
        return place;
    }

    private ScheduleItem newItem() {
        return ScheduleItem.create(day, confirmedPlace(), 1000, LocalTime.of(11, 0), "점심");
    }

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("확정된 장소로 블록을 만들면 날, 장소, 순서값, 시간, 메모를 보유한다")
            void createsItem() {
                PlaceCandidate place = confirmedPlace();

                ScheduleItem item =
                        ScheduleItem.create(day, place, 1000, LocalTime.of(11, 0), "점심");

                assertThat(item.getScheduleDay()).isSameAs(day);
                assertThat(item.getPlaceCandidate()).isSameAs(place);
                assertThat(item.getSortOrder()).isEqualTo(1000);
                assertThat(item.getStartTime()).isEqualTo(LocalTime.of(11, 0));
                assertThat(item.getMemo()).isEqualTo("점심");
            }

            @Test
            @DisplayName("시간과 메모 없이도 블록을 만들 수 있다")
            void createsItemWithoutTimeAndMemo() {
                ScheduleItem item = ScheduleItem.create(day, confirmedPlace(), 1000, null, null);

                assertThat(item.getStartTime()).isNull();
                assertThat(item.getMemo()).isNull();
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("확정되지 않은 장소로 블록을 만들면 PLACE_NOT_CONFIRMED 에러가 난다")
            void rejectsUnconfirmedPlace() {
                PlaceCandidate place = newPlace();

                assertThatThrownBy(() -> ScheduleItem.create(day, place, 1000, null, null))
                        .isInstanceOf(BusinessException.class)
                        .extracting("errorCode")
                        .isEqualTo(PlaceErrorCode.PLACE_NOT_CONFIRMED);
            }
        }
    }

    @Nested
    class Update {

        @Nested
        class Success {

            @Test
            @DisplayName("블록을 수정하면 시간과 메모가 새 값으로 바뀐다")
            void updatesTimeAndMemo() {
                ScheduleItem item = newItem();

                item.update(LocalTime.of(15, 0), "시간 변경");

                assertThat(item.getStartTime()).isEqualTo(LocalTime.of(15, 0));
                assertThat(item.getMemo()).isEqualTo("시간 변경");
            }

            @Test
            @DisplayName("블록을 null로 수정하면 시간과 메모가 비워진다")
            void clearsTimeAndMemo() {
                ScheduleItem item = newItem();

                item.update(null, null);

                assertThat(item.getStartTime()).isNull();
                assertThat(item.getMemo()).isNull();
            }

            @Test
            @DisplayName("순서값을 바꾸면 새 순서값을 보유한다")
            void changesSortOrder() {
                ScheduleItem item = newItem();

                item.changeSortOrder(1500);

                assertThat(item.getSortOrder()).isEqualTo(1500);
            }

            @Test
            @DisplayName("다른 날로 옮기면 날과 순서값이 바뀌고, 시간과 메모는 그대로다")
            void movesToOtherDay() {
                ScheduleItem item = newItem();
                ScheduleDay day2 = ScheduleDay.create(room, 2);

                item.moveTo(day2, 2500);

                assertThat(item.getScheduleDay()).isSameAs(day2);
                assertThat(item.getSortOrder()).isEqualTo(2500);
                assertThat(item.getStartTime()).isEqualTo(LocalTime.of(11, 0));
                assertThat(item.getMemo()).isEqualTo("점심");
            }
        }
    }
}
