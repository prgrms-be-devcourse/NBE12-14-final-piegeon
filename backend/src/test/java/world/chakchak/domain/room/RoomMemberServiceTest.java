package world.chakchak.domain.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.errorcode.RoomErrorCode;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.room.service.RoomMemberService;
import world.chakchak.domain.user.entity.User;
import world.chakchak.global.error.BusinessException;
import world.chakchak.global.error.CommonErrorCode;
import world.chakchak.global.error.ErrorCode;

@ExtendWith(MockitoExtension.class)
class RoomMemberServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 6, 1, 12, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
    private static final Long ROOM_ID = 1L;
    private static final Long USER_ID = 100L;

    @Mock private RoomRepository roomRepository;
    @Mock private RoomMemberRepository roomMemberRepository;

    @InjectMocks private RoomMemberService roomMemberService;

    private static RoomMember member() {
        Room room =
                Room.create(
                        "여름 여행",
                        "부산",
                        LocalDate.of(2027, 7, 1),
                        LocalDate.of(2027, 7, 3),
                        "ABCD1234",
                        RoomStatus.SURVEYING,
                        CLOCK);
        return RoomMember.create(room, User.create("minsu@example.com", "민수", "hash"), CLOCK);
    }

    private static void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("방에서 나가지 않은 멤버면 방 존재 확인 없이 그 멤버를 돌려준다")
            void returnsActiveMember() {
                RoomMember member = member();
                given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, USER_ID))
                        .willReturn(Optional.of(member));

                assertThat(roomMemberService.getActiveMember(ROOM_ID, USER_ID)).isSameAs(member);
                verify(roomRepository, never()).existsById(any());
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("없는 방이면 ROOM_NOT_FOUND를 던진다")
            void rejectsMissingRoom() {
                given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, USER_ID))
                        .willReturn(Optional.empty());
                given(roomRepository.existsById(ROOM_ID)).willReturn(false);

                assertErrorCode(
                        () -> roomMemberService.getActiveMember(ROOM_ID, USER_ID),
                        RoomErrorCode.ROOM_NOT_FOUND);
            }

            @Test
            @DisplayName("방은 있지만 멤버가 아니면 FORBIDDEN을 던진다")
            void rejectsNonMember() {
                given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, USER_ID))
                        .willReturn(Optional.empty());
                given(roomRepository.existsById(ROOM_ID)).willReturn(true);

                assertErrorCode(
                        () -> roomMemberService.getActiveMember(ROOM_ID, USER_ID),
                        CommonErrorCode.FORBIDDEN);
            }

            @Test
            @DisplayName("방에서 나간 멤버면 FORBIDDEN을 던진다")
            void rejectsLeftMember() {
                RoomMember member = member();
                ReflectionTestUtils.setField(member, "leftAt", NOW);
                given(roomMemberRepository.findByRoom_IdAndUser_Id(ROOM_ID, USER_ID))
                        .willReturn(Optional.of(member));

                assertErrorCode(
                        () -> roomMemberService.getActiveMember(ROOM_ID, USER_ID),
                        CommonErrorCode.FORBIDDEN);
            }
        }
    }
}
