package world.chakchak.domain.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomStatus;

class RoomTest {

    @Test
    @DisplayName("방은 전달받은 기본 정보와 상태를 보유한다")
    void createsRoom() {
        Room room =
                Room.create(
                        "여름 여행",
                        "일본",
                        LocalDate.of(2027, 7, 1),
                        LocalDate.of(2027, 7, 3),
                        "ABCD1234",
                        RoomStatus.SURVEYING,
                        Clock.fixed(
                                LocalDateTime.of(2027, 1, 1, 12, 0)
                                        .atZone(ZoneId.of("Asia/Seoul"))
                                        .toInstant(),
                                ZoneId.of("Asia/Seoul")));

        assertThat(room.getName()).isEqualTo("여름 여행");
        assertThat(room.getDestination()).isEqualTo("일본");
        assertThat(room.getStartDate()).isEqualTo(LocalDate.of(2027, 7, 1));
        assertThat(room.getEndDate()).isEqualTo(LocalDate.of(2027, 7, 3));
        assertThat(room.getInviteCode()).isEqualTo("ABCD1234");
        assertThat(room.getStatus()).isEqualTo(RoomStatus.SURVEYING);
        assertThat(room.getOpinionDeadlineAt()).isEqualTo(LocalDateTime.of(2027, 1, 4, 12, 0));
        assertThat(room.getSurveyClosedAt()).isNull();
        assertThat(room.getFinalizedAt()).isNull();
    }

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 6, 1, 12, 0);

    private static Room roomIn(RoomStatus status) {
        return Room.create(
                "여름 여행",
                "부산",
                LocalDate.of(2027, 7, 1),
                LocalDate.of(2027, 7, 3),
                "ABCD1234",
                status,
                Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));
    }

    @Nested
    class Update {

        @Nested
        class Success {

            @Test
            @DisplayName("설문 조율이 끝나면 SURVEYING에서 PLANNING으로 넘어간다")
            void startsPlanning() {
                Room room = roomIn(RoomStatus.SURVEYING);

                room.startPlanning();

                assertThat(room.getStatus()).isEqualTo(RoomStatus.PLANNING);
            }

            @Test
            @DisplayName("최종 확정에 동의로 결정되면 FINALIZED가 되고 확정 시각이 기록된다")
            void finalizesPlan() {
                Room room = roomIn(RoomStatus.REVIEWING);

                room.finalizePlan(NOW);

                assertThat(room.getStatus()).isEqualTo(RoomStatus.FINALIZED);
                assertThat(room.getFinalizedAt()).isEqualTo(NOW);
            }

            @Test
            @DisplayName("최종 확정 투표가 반대나 미결로 끝나면 PLANNING으로 돌아간다")
            void returnsToPlanning() {
                Room room = roomIn(RoomStatus.REVIEWING);

                room.returnToPlanning();

                assertThat(room.getStatus()).isEqualTo(RoomStatus.PLANNING);
                assertThat(room.getFinalizedAt()).isNull();
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("SURVEYING이 아닌 방은 PLANNING으로 넘길 수 없다")
            void rejectsStartPlanningOutsideSurveying() {
                Room room = roomIn(RoomStatus.PLANNING);

                assertThatThrownBy(room::startPlanning).isInstanceOf(IllegalStateException.class);
            }

            @Test
            @DisplayName("REVIEWING이 아닌 방은 최종 확정할 수 없다")
            void rejectsFinalizeOutsideReviewing() {
                Room room = roomIn(RoomStatus.PLANNING);

                assertThatThrownBy(() -> room.finalizePlan(NOW))
                        .isInstanceOf(IllegalStateException.class);
                assertThat(room.getFinalizedAt()).isNull();
            }

            @Test
            @DisplayName("REVIEWING이 아닌 방은 PLANNING으로 되돌릴 수 없다")
            void rejectsReturnOutsideReviewing() {
                Room room = roomIn(RoomStatus.FINALIZED);

                assertThatThrownBy(room::returnToPlanning)
                        .isInstanceOf(IllegalStateException.class);
            }
        }
    }
}
