package world.chakchak.domain.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.agenda.entity.AgendaSubject;
import world.chakchak.domain.changelog.event.RoomEvent;
import world.chakchak.domain.comment.dto.CommentResponse;
import world.chakchak.domain.comment.entity.Comment;
import world.chakchak.domain.comment.event.CommentCreatedEvent;
import world.chakchak.domain.comment.repository.CommentRepository;
import world.chakchak.domain.comment.service.CommentService;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.user.entity.User;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 6, 1, 12, 0);
    private static final Long ROOM_ID = 1L;

    @Mock private CommentRepository commentRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

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

            @Test
            @DisplayName("장소 코멘트를 남기면 장소 후보와 작성자, 본문을 담아 저장하고 안건은 비워 둔다")
            void savesPlaceComment() {
                Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
                Room room =
                        Room.create(
                                "여름 여행",
                                "부산",
                                LocalDate.of(2027, 7, 1),
                                LocalDate.of(2027, 7, 3),
                                "ABCD1234",
                                RoomStatus.PLANNING,
                                clock);
                RoomMember member =
                        RoomMember.create(room, User.create("a@example.com", "민수", "hash"), clock);
                PlaceCandidate place = place(room, member);
                given(commentRepository.save(any(Comment.class)))
                        .willAnswer(invocation -> invocation.getArgument(0));

                Comment saved = commentService.addOnPlace(place, member, "줄이 길대요");

                assertThat(saved.getPlaceCandidate()).isSameAs(place);
                assertThat(saved.getMember()).isSameAs(member);
                assertThat(saved.getAgendaItem()).isNull();
                assertThat(saved.getBody()).isEqualTo("줄이 길대요");
            }

            @Test
            @DisplayName("안건 코멘트를 남기면 작성자의 방에 comment.created를 id 없이 보내고 data에 안건 id와 코멘트를 담는다")
            void publishesAgendaCommentCreated() {
                Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
                Room room = roomWithId(RoomStatus.SURVEYING, clock);
                RoomMember member =
                        RoomMember.create(room, User.create("a@example.com", "민수", "hash"), clock);
                AgendaItem agenda =
                        AgendaItem.create(
                                room, AgendaSubject.BUDGET, 1, "예산", 2, NOW, NOW.plusDays(3));
                ReflectionTestUtils.setField(agenda, "id", 5L);
                given(commentRepository.save(any(Comment.class)))
                        .willAnswer(invocation -> withId(invocation.getArgument(0), 30L));

                commentService.addOnAgenda(agenda, member, "100만원은 부담돼요");

                RoomEvent event = publishedEvent();
                assertThat(event.roomId()).isEqualTo(ROOM_ID);
                assertThat(event.changeLogId()).isNull();
                assertThat(event.name()).isEqualTo("comment.created");
                assertThat(event.data())
                        .isInstanceOfSatisfying(
                                CommentCreatedEvent.class,
                                data -> {
                                    assertThat(data.agendaId()).isEqualTo(5L);
                                    assertThat(data.placeId()).isNull();
                                    assertThat(data.comment().commentId()).isEqualTo(30L);
                                    assertThat(data.comment().name()).isEqualTo("민수");
                                    assertThat(data.comment().body()).isEqualTo("100만원은 부담돼요");
                                });
            }

            @Test
            @DisplayName("장소 코멘트를 남기면 작성자의 방에 comment.created를 id 없이 보내고 data에 장소 id와 코멘트를 담는다")
            void publishesPlaceCommentCreated() {
                Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
                Room room = roomWithId(RoomStatus.PLANNING, clock);
                RoomMember member =
                        RoomMember.create(room, User.create("a@example.com", "민수", "hash"), clock);
                PlaceCandidate place = place(room, member);
                ReflectionTestUtils.setField(place, "id", 7L);
                given(commentRepository.save(any(Comment.class)))
                        .willAnswer(invocation -> withId(invocation.getArgument(0), 31L));

                commentService.addOnPlace(place, member, "줄이 길대요");

                RoomEvent event = publishedEvent();
                assertThat(event.roomId()).isEqualTo(ROOM_ID);
                assertThat(event.changeLogId()).isNull();
                assertThat(event.name()).isEqualTo("comment.created");
                assertThat(event.data())
                        .isInstanceOfSatisfying(
                                CommentCreatedEvent.class,
                                data -> {
                                    assertThat(data.agendaId()).isNull();
                                    assertThat(data.placeId()).isEqualTo(7L);
                                    assertThat(data.comment().commentId()).isEqualTo(31L);
                                    assertThat(data.comment().body()).isEqualTo("줄이 길대요");
                                });
            }
        }
    }

    private static Room roomWithId(RoomStatus status, Clock clock) {
        Room room =
                Room.create(
                        "여름 여행",
                        "부산",
                        LocalDate.of(2027, 7, 1),
                        LocalDate.of(2027, 7, 3),
                        "ABCD1234",
                        status,
                        clock);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        return room;
    }

    // 저장하면 DB가 id를 채우므로 테스트에서는 직접 넣는다
    private static Comment withId(Comment comment, Long id) {
        ReflectionTestUtils.setField(comment, "id", id);
        return comment;
    }

    private RoomEvent publishedEvent() {
        ArgumentCaptor<RoomEvent> captor = ArgumentCaptor.forClass(RoomEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    private static PlaceCandidate place(Room room, RoomMember member) {
        return PlaceCandidate.create(
                room,
                "place-1",
                "광장시장",
                "market",
                "서울 종로구",
                "https://maps.google.com/?cid=1",
                null,
                member);
    }

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("안건 코멘트를 저장소가 준 오래된 순 그대로 작성자 이름과 함께 응답으로 바꾼다")
            void mapsAgendaCommentsInOrder() {
                Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
                Room room =
                        Room.create(
                                "여름 여행",
                                "부산",
                                LocalDate.of(2027, 7, 1),
                                LocalDate.of(2027, 7, 3),
                                "ABCD1234",
                                RoomStatus.SURVEYING,
                                clock);
                RoomMember minsu =
                        RoomMember.create(room, User.create("a@example.com", "민수", "hash"), clock);
                RoomMember jieun =
                        RoomMember.create(room, User.create("b@example.com", "지은", "hash"), clock);
                ReflectionTestUtils.setField(minsu, "id", 11L);
                ReflectionTestUtils.setField(jieun, "id", 12L);
                AgendaItem agenda =
                        AgendaItem.create(
                                room, AgendaSubject.BUDGET, 1, "예산", 2, NOW, NOW.plusDays(3));
                Comment first = Comment.onAgenda(agenda, minsu, "150만 원은 부담돼요");
                Comment second = Comment.onAgenda(agenda, jieun, "저는 괜찮아요");
                ReflectionTestUtils.setField(first, "id", 7L);
                ReflectionTestUtils.setField(first, "createdAt", NOW);
                ReflectionTestUtils.setField(second, "id", 8L);
                ReflectionTestUtils.setField(second, "createdAt", NOW.plusMinutes(5));
                given(commentRepository.findAllByAgendaItemIdOrderByIdAsc(3L))
                        .willReturn(List.of(first, second));

                List<CommentResponse> responses = commentService.getOnAgenda(3L);

                assertThat(responses)
                        .containsExactly(
                                new CommentResponse(7L, 11L, "민수", "150만 원은 부담돼요", NOW),
                                new CommentResponse(8L, 12L, "지은", "저는 괜찮아요", NOW.plusMinutes(5)));
            }

            @Test
            @DisplayName("장소 코멘트를 저장소가 준 오래된 순 그대로 작성자 이름과 함께 응답으로 바꾼다")
            void mapsPlaceCommentsInOrder() {
                Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
                Room room =
                        Room.create(
                                "여름 여행",
                                "부산",
                                LocalDate.of(2027, 7, 1),
                                LocalDate.of(2027, 7, 3),
                                "ABCD1234",
                                RoomStatus.PLANNING,
                                clock);
                RoomMember minsu =
                        RoomMember.create(room, User.create("a@example.com", "민수", "hash"), clock);
                ReflectionTestUtils.setField(minsu, "id", 11L);
                Comment comment = Comment.onPlace(place(room, minsu), minsu, "줄이 길대요");
                ReflectionTestUtils.setField(comment, "id", 9L);
                ReflectionTestUtils.setField(comment, "createdAt", NOW);
                given(commentRepository.findAllByPlaceCandidateIdOrderByIdAsc(4L))
                        .willReturn(List.of(comment));

                assertThat(commentService.getOnPlace(4L))
                        .containsExactly(new CommentResponse(9L, 11L, "민수", "줄이 길대요", NOW));
            }
        }
    }
}
