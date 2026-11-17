package world.chakchak.domain.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
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
                        "부산",
                        LocalDate.of(2027, 7, 1),
                        LocalDate.of(2027, 7, 3),
                        "ABCD1234",
                        RoomStatus.SURVEYING,
                        null,
                        null,
                        null);

        assertThat(room.getName()).isEqualTo("여름 여행");
        assertThat(room.getDestination()).isEqualTo("부산");
        assertThat(room.getStartDate()).isEqualTo(LocalDate.of(2027, 7, 1));
        assertThat(room.getEndDate()).isEqualTo(LocalDate.of(2027, 7, 3));
        assertThat(room.getInviteCode()).isEqualTo("ABCD1234");
        assertThat(room.getStatus()).isEqualTo(RoomStatus.SURVEYING);
    }
}
