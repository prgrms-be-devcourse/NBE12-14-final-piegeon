package world.chakchak.domain.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.user.entity.User;

class RoomMemberTest {

    @Test
    @DisplayName("최초 합류 시각과 마지막 조회 시각을 같은 현재 시각으로 설정한다")
    void initializesMembershipTimestamps() {
        Clock clock =
                Clock.fixed(
                        LocalDateTime.of(2027, 1, 1, 12, 0)
                                .atZone(ZoneId.of("Asia/Seoul"))
                                .toInstant(),
                        ZoneId.of("Asia/Seoul"));
        Room room =
                Room.create(
                        "여름 여행",
                        "부산",
                        LocalDate.of(2027, 7, 1),
                        LocalDate.of(2027, 7, 3),
                        "ABCD1234",
                        RoomStatus.SURVEYING,
                        clock);
        User user = User.create("tester@example.com", "테스터", "hashed-password");

        RoomMember member = RoomMember.create(room, user, clock);

        assertThat(member.getRoom()).isSameAs(room);
        assertThat(member.getUser()).isSameAs(user);
        assertThat(member.getJoinedAt()).isEqualTo(LocalDateTime.of(2027, 1, 1, 12, 0));
        assertThat(member.getLastSeenAt()).isEqualTo(member.getJoinedAt());
        assertThat(member.getLeftAt()).isNull();
    }
}
