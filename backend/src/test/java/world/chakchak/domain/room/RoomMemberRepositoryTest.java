package world.chakchak.domain.room;

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
import org.springframework.jdbc.core.JdbcTemplate;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;

@IntegrationTest
class RoomMemberRepositoryTest {

    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Clock clock;

    @Nested
    class Create {

        @Test
        @DisplayName("멤버를 저장하면 방과 사용자 연관관계를 저장하고 다시 조회한다")
        void savesAndFindsRoomAndUserAssociations() {
            Room room = saveRoom();
            User user = saveUser();
            RoomMember saved =
                    roomMemberRepository.saveAndFlush(RoomMember.create(room, user, clock));

            RoomMember found = roomMemberRepository.findById(saved.getId()).orElseThrow();

            assertThat(found.getRoom().getId()).isEqualTo(room.getId());
            assertThat(found.getUser().getId()).isEqualTo(user.getId());
            assertThat(found.getJoinedAt()).isEqualTo(found.getLastSeenAt());
            assertThat(found.getCreatedAt()).isNotNull();
            assertThat(found.getModifiedAt()).isNotNull();
        }

        @Test
        @DisplayName("같은 방과 사용자의 멤버 데이터는 복합 UNIQUE 제약조건으로 중복 저장할 수 없다")
        void rejectsDuplicateRoomAndUser() {
            Room room = saveRoom();
            User user = saveUser();
            roomMemberRepository.saveAndFlush(RoomMember.create(room, user, clock));

            assertThatThrownBy(() ->
                            roomMemberRepository.saveAndFlush(RoomMember.create(room, user, clock)))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    class Read {

        @Test
        @DisplayName("방 ID와 사용자 ID로 멤버를 조회한다")
        void findsByRoomAndUserIds() {
            Room room = saveRoom();
            User user = saveUser();
            RoomMember saved =
                    roomMemberRepository.saveAndFlush(RoomMember.create(room, user, clock));

            assertThat(
                            roomMemberRepository
                                    .findByRoom_IdAndUser_Id(room.getId(), user.getId())
                                    .map(RoomMember::getId))
                    .contains(saved.getId());
        }

        @Test
        @DisplayName("방 ID와 사용자 ID별 활동 중인 멤버를 조회하고 탈퇴한 멤버는 제외한다")
        void findsActiveMembersByRoomAndUser() {
            Room room = saveRoom();
            User activeUser = saveUser();
            User departedUser = saveUser();
            RoomMember active =
                    roomMemberRepository.saveAndFlush(RoomMember.create(room, activeUser, clock));
            RoomMember departed =
                    roomMemberRepository.saveAndFlush(RoomMember.create(room, departedUser, clock));
            jdbcTemplate.update(
                    "UPDATE plan_room_member SET left_at = CURRENT_TIMESTAMP(6) WHERE id = ?",
                    departed.getId());

            assertThat(roomMemberRepository.findAllByRoom_IdAndLeftAtIsNull(room.getId()))
                    .extracting(member -> member.getId())
                    .containsExactly(active.getId());
            assertThat(roomMemberRepository.findAllByUser_IdAndLeftAtIsNull(activeUser.getId()))
                    .extracting(member -> member.getId())
                    .containsExactly(active.getId());
            assertThat(roomMemberRepository.findAllByUser_IdAndLeftAtIsNull(departedUser.getId()))
                    .isEmpty();
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
                        RoomStatus.SURVEYING,
                        clock));
    }

    private User saveUser() {
        String suffix = UUID.randomUUID().toString();
        return userRepository.saveAndFlush(
                User.create(suffix + "@example.com", "테스터", "hashed-password"));
    }
}
