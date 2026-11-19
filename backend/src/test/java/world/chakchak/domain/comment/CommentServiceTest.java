package world.chakchak.domain.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.agenda.entity.AgendaSubject;
import world.chakchak.domain.comment.entity.Comment;
import world.chakchak.domain.comment.repository.CommentRepository;
import world.chakchak.domain.comment.service.CommentService;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 6, 1, 12, 0);

    @Mock private CommentRepository commentRepository;

    @InjectMocks private CommentService commentService;

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("안건 코멘트를 남기면 안건과 작성자, 본문을 담아 저장한다")
            void savesAgendaComment() {
                Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
                Room room =
                        Room.create(
                                "여름 여행",
                                "부산",
                                LocalDate.of(2027, 7, 1),
                                LocalDate.of(2027, 7, 3),
                                "ABCD1234",
                                RoomStatus.REVIEWING,
                                clock);
                RoomMember member =
                        RoomMember.create(room, User.create("a@example.com", "민수", "hash"), clock);
                AgendaItem agenda =
                        AgendaItem.create(
                                room,
                                AgendaSubject.FINAL_PLAN,
                                1,
                                "확정할까요?",
                                2,
                                NOW,
                                NOW.plusDays(3));
                given(commentRepository.save(any(Comment.class)))
                        .willAnswer(invocation -> invocation.getArgument(0));

                Comment saved = commentService.addOnAgenda(agenda, member, "일정이 빡빡해요");

                assertThat(saved.getAgendaItem()).isSameAs(agenda);
                assertThat(saved.getMember()).isSameAs(member);
                assertThat(saved.getPlaceCandidate()).isNull();
                assertThat(saved.getBody()).isEqualTo("일정이 빡빡해요");
            }
        }
    }
}
