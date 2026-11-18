package world.chakchak.domain.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.agenda.entity.AgendaSubject;
import world.chakchak.domain.comment.entity.Comment;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.user.entity.User;

class CommentTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 6, 1, 12, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);

    private static final Room ROOM =
            Room.create(
                    "여름 여행",
                    "부산",
                    LocalDate.of(2027, 7, 1),
                    LocalDate.of(2027, 7, 3),
                    "ABCD1234",
                    RoomStatus.PLANNING,
                    CLOCK);

    private static RoomMember member() {
        return RoomMember.create(ROOM, User.create("jieun@example.com", "지은", "hash"), CLOCK);
    }

    private static AgendaItem agenda() {
        return AgendaItem.create(
                ROOM, AgendaSubject.BUDGET, 1, "예산을 정해주세요", 3, NOW, NOW.plusDays(4));
    }

    private static PlaceCandidate place() {
        return PlaceCandidate.create(ROOM, "place-1", "광장시장", "market", null, null, null, member());
    }

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("안건 코멘트는 안건과 작성자, 본문을 기록하고 장소는 비어 있다")
            void createsAgendaComment() {
                AgendaItem agenda = agenda();
                RoomMember member = member();

                Comment comment = Comment.onAgenda(agenda, member, "150은 좀 부담돼요");

                assertThat(comment.getAgendaItem()).isSameAs(agenda);
                assertThat(comment.getPlaceCandidate()).isNull();
                assertThat(comment.getMember()).isSameAs(member);
                assertThat(comment.getBody()).isEqualTo("150은 좀 부담돼요");
            }

            @Test
            @DisplayName("장소 코멘트는 장소와 작성자, 본문을 기록하고 안건은 비어 있다")
            void createsPlaceComment() {
                PlaceCandidate place = place();

                Comment comment = Comment.onPlace(place, member(), "줄이 길대요");

                assertThat(comment.getPlaceCandidate()).isSameAs(place);
                assertThat(comment.getAgendaItem()).isNull();
            }

            @Test
            @DisplayName("본문은 1자부터 500자까지 쓸 수 있다")
            void acceptsBodyLengthBoundaries() {
                assertThat(Comment.onAgenda(agenda(), member(), "좋").getBody()).hasSize(1);
                assertThat(Comment.onAgenda(agenda(), member(), "가".repeat(500)).getBody())
                        .hasSize(500);
            }
        }

        @Nested
        class Exception {

            @ParameterizedTest
            @NullSource
            @ValueSource(strings = {"", " ", "\n\t"})
            @DisplayName("비어 있거나 공백뿐인 본문으로는 코멘트를 만들 수 없다")
            void rejectsBlankBody(String body) {
                assertThatThrownBy(() -> Comment.onAgenda(agenda(), member(), body))
                        .isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> Comment.onPlace(place(), member(), body))
                        .isInstanceOf(IllegalArgumentException.class);
            }

            @Test
            @DisplayName("500자를 넘는 본문으로는 코멘트를 만들 수 없다")
            void rejectsTooLongBody() {
                String body = "가".repeat(501);

                assertThatThrownBy(() -> Comment.onAgenda(agenda(), member(), body))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }
    }
}
