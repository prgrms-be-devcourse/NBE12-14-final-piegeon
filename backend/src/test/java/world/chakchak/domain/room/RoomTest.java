package world.chakchak.domain.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
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
}
