package world.chakchak.domain.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import world.chakchak.domain.agenda.dto.AddOptionRequest;
import world.chakchak.domain.agenda.dto.AgendaDetailResponse;
import world.chakchak.domain.agenda.dto.AgendaDetailResponse.OptionResult;
import world.chakchak.domain.agenda.dto.AgendaListResponse;
import world.chakchak.domain.agenda.dto.MemberRef;
import world.chakchak.domain.agenda.dto.OptionRef;
import world.chakchak.domain.agenda.dto.VoteRequest;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.agenda.entity.AgendaOption;
import world.chakchak.domain.agenda.entity.AgendaStatus;
import world.chakchak.domain.agenda.entity.AgendaSubject;
import world.chakchak.domain.agenda.entity.FinalPlanChoice;
import world.chakchak.domain.agenda.entity.Vote;
import world.chakchak.domain.agenda.errorcode.AgendaErrorCode;
import world.chakchak.domain.agenda.repository.AgendaItemRepository;
import world.chakchak.domain.agenda.repository.AgendaOptionRepository;
import world.chakchak.domain.agenda.repository.VoteRepository;
import world.chakchak.domain.agenda.service.AgendaService;
import world.chakchak.domain.comment.dto.CommentListResponse;
import world.chakchak.domain.comment.dto.CommentRequest;
import world.chakchak.domain.comment.dto.CommentResponse;
import world.chakchak.domain.comment.entity.Comment;
import world.chakchak.domain.comment.service.CommentService;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.errorcode.RoomErrorCode;
import world.chakchak.domain.room.service.RoomMemberService;
import world.chakchak.domain.room.service.RoomService;
import world.chakchak.domain.survey.entity.Pace;
import world.chakchak.domain.survey.entity.StayType;
import world.chakchak.domain.user.entity.User;
import world.chakchak.global.error.BusinessException;
import world.chakchak.global.error.CommonErrorCode;
import world.chakchak.global.error.ErrorCode;

@ExtendWith(MockitoExtension.class)
class AgendaServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 6, 1, 12, 0);
    private static final Long ROOM_ID = 1L;
    private static final Long USER_ID = 100L;

    @Mock private RoomService roomService;
    @Mock private RoomMemberService roomMemberService;
    @Mock private CommentService commentService;
    @Mock private Clock clock;
    @Mock private AgendaItemRepository agendaItemRepository;
    @Mock private AgendaOptionRepository agendaOptionRepository;
    @Mock private VoteRepository voteRepository;

    @InjectMocks private AgendaService agendaService;

    private Room room;
    // 나는 NOW - 1일에 합류했다
    private RoomMember me;
    private RoomMember jieun;

    // 엔티티 id는 DB가 채우므로 테스트에서는 직접 넣는다
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        room =
                Room.create(
                        "여름 여행",
                        "부산",
                        LocalDate.of(2027, 7, 1),
                        LocalDate.of(2027, 7, 3),
                        "ABCD1234",
                        RoomStatus.SURVEYING,
                        clockAt(NOW.minusDays(3)));
        me = member("민수", NOW.minusDays(1));
        jieun = member("지은", NOW.minusDays(1));
    }

    private static Clock clockAt(LocalDateTime time) {
        return Clock.fixed(time.atZone(SEOUL).toInstant(), SEOUL);
    }

    private <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", nextId++);
        return entity;
    }

    private RoomMember member(String name, LocalDateTime joinedAt) {
        User user = User.create(name + "@example.com", name, "hash");
        return withId(RoomMember.create(room, user, clockAt(joinedAt)));
    }

    /** createdAt에 저장된 투표 중 안건 */
    private AgendaItem agenda(
            AgendaSubject subject, LocalDateTime createdAt, LocalDateTime deadlineAt) {
        AgendaItem agenda =
                withId(AgendaItem.create(room, subject, 1, "안건 제목", 2, createdAt, deadlineAt));
        ReflectionTestUtils.setField(agenda, "createdAt", createdAt);
        return agenda;
    }

    private AgendaItem votingAgenda(LocalDateTime deadlineAt) {
        return agenda(AgendaSubject.BUDGET, NOW.minusHours(1), deadlineAt);
    }

    private AgendaOption budget(AgendaItem agenda, long amount, int index, RoomMember by) {
        return withId(AgendaOption.budget(agenda, amount, index, by));
    }

    private Vote vote(AgendaItem agenda, AgendaOption option, RoomMember member) {
        return withId(Vote.create(agenda, option, member, NOW));
    }

    private void givenAgendas(List<AgendaItem> voting, List<AgendaItem> closed) {
        given(
                        agendaItemRepository.findAllByRoomIdAndStatusOrderByDeadlineAtAscIdAsc(
                                ROOM_ID, AgendaStatus.VOTING))
                .willReturn(voting);
        given(
                        agendaItemRepository.findAllByRoomIdAndStatusNotOrderByCreatedAtDescIdDesc(
                                ROOM_ID, AgendaStatus.VOTING))
                .willReturn(closed);
    }

    private void givenActiveMember() {
        given(roomMemberService.getActiveMember(ROOM_ID, USER_ID)).willReturn(me);
    }

    private static List<Long> idsOf(List<AgendaListResponse.Item> responses) {
        return responses.stream().map(AgendaListResponse.Item::agendaId).toList();
    }

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("안건 코멘트 목록은 이 방의 안건임을 확인한 뒤 코멘트를 comments로 감싸 돌려준다")
            void returnsAgendaComments() {
                givenActiveMember();
                AgendaItem agenda = votingAgenda(NOW.plusDays(3));
                given(agendaItemRepository.findByIdAndRoomId(agenda.getId(), ROOM_ID))
                        .willReturn(Optional.of(agenda));
                List<CommentResponse> comments =
                        List.of(new CommentResponse(7L, me.getId(), "민수", "150만 원은 부담돼요", NOW));
                given(commentService.getOnAgenda(agenda.getId())).willReturn(comments);

                CommentListResponse response =
                        agendaService.getComments(ROOM_ID, agenda.getId(), USER_ID);

                assertThat(response.comments()).isEqualTo(comments);
            }

            @Test
            @DisplayName("안건 목록은 투표 중인 안건 뒤에 끝난 안건을 이어 붙인다")
            void sortsVotingFirstThenRecent() {
                AgendaItem laterDeadline = votingAgenda(NOW.plusDays(3));
                AgendaItem soonerDeadline = votingAgenda(NOW.plusDays(2));
                AgendaItem olderClosed =
                        agenda(AgendaSubject.STAY, NOW.minusDays(5), NOW.plusDays(1));
                olderClosed.expire();
                AgendaItem newerClosed =
                        agenda(AgendaSubject.PACE, NOW.minusDays(2), NOW.plusDays(1));
                newerClosed.expire();
                givenActiveMember();
                // repository가 각각 정렬해서 돌려준다 (정렬은 AgendaItemRepositoryTest에서 확인)
                givenAgendas(
                        List.of(soonerDeadline, laterDeadline), List.of(newerClosed, olderClosed));
                given(voteRepository.findAllByAgendaItemIdIn(anyCollection()))
                        .willReturn(List.of());

                List<AgendaListResponse.Item> result =
                        agendaService.getAgendas(ROOM_ID, USER_ID).agendas();

                assertThat(idsOf(result))
                        .containsExactly(
                                soonerDeadline.getId(),
                                laterDeadline.getId(),
                                newerClosed.getId(),
                                olderClosed.getId());
            }

            @Test
            @DisplayName("안건 목록의 각 안건에 표 수, 내 선택, 투표 자격, 결정된 선지를 채운다")
            void fillsVoteSummary() {
                AgendaItem voting = votingAgenda(NOW.plusDays(2));
                AgendaOption low = budget(voting, 600_000L, 0, null);
                AgendaItem resolved =
                        agenda(AgendaSubject.FINAL_PLAN, NOW.minusHours(2), NOW.plusDays(1));
                AgendaOption agree =
                        withId(AgendaOption.finalPlan(resolved, FinalPlanChoice.AGREE));
                resolved.resolve(agree, NOW);
                givenActiveMember();
                givenAgendas(List.of(voting), List.of(resolved));
                given(voteRepository.findAllByAgendaItemIdIn(anyCollection()))
                        .willReturn(List.of(vote(voting, low, me), vote(voting, low, jieun)));
                given(agendaOptionRepository.findAllById(List.of(agree.getId())))
                        .willReturn(List.of(agree));

                List<AgendaListResponse.Item> result =
                        agendaService.getAgendas(ROOM_ID, USER_ID).agendas();

                AgendaListResponse.Item first = result.getFirst();
                assertThat(first.votedCount()).isEqualTo(2);
                assertThat(first.myOptionId()).isEqualTo(low.getId());
                assertThat(first.eligible()).isTrue();
                assertThat(first.resolvedOption()).isNull();
                AgendaListResponse.Item second = result.get(1);
                assertThat(second.votedCount()).isZero();
                assertThat(second.myOptionId()).isNull();
                assertThat(second.resolvedOption())
                        .isEqualTo(new OptionRef(agree.getId(), "동의", agree.getValue()));
            }

            @Test
            @DisplayName("내가 합류하기 전에 열린 안건도 목록에 나오고 투표 자격은 없다고 표시한다")
            void listsAgendaOpenedBeforeJoin() {
                // 내가 합류(NOW - 1일)하기 전에 열린 안건
                AgendaItem beforeJoin =
                        agenda(AgendaSubject.STAY, NOW.minusDays(2), NOW.plusDays(2));
                givenActiveMember();
                givenAgendas(List.of(beforeJoin), List.of());
                given(voteRepository.findAllByAgendaItemIdIn(anyCollection()))
                        .willReturn(List.of());

                List<AgendaListResponse.Item> result =
                        agendaService.getAgendas(ROOM_ID, USER_ID).agendas();

                assertThat(idsOf(result)).containsExactly(beforeJoin.getId());
                assertThat(result.getFirst().eligible()).isFalse();
            }

            @Test
            @DisplayName("방에 안건이 없으면 표를 조회하지 않고 빈 목록을 돌려준다")
            void returnsEmptyWithoutQueryingVotes() {
                givenActiveMember();
                givenAgendas(List.of(), List.of());

                assertThat(agendaService.getAgendas(ROOM_ID, USER_ID).agendas()).isEmpty();
                verifyNoInteractions(voteRepository);
            }

            @Test
            @DisplayName("안건 상세는 선지를 화면 순서대로 두고 선지마다 표 수, 투표자, 추가한 멤버를 채운다")
            void buildsDetail() {
                AgendaItem agenda = votingAgenda(NOW.plusDays(2));
                AgendaOption low = budget(agenda, 600_000L, 0, null);
                AgendaOption proposed = budget(agenda, 1_500_000L, 1, jieun);
                Vote jieunVote = vote(agenda, low, jieun);
                Vote myVote = vote(agenda, low, me);
                givenActiveMember();
                given(agendaItemRepository.findByIdAndRoomId(agenda.getId(), ROOM_ID))
                        .willReturn(Optional.of(agenda));
                given(
                                agendaOptionRepository.findAllByAgendaItemIdOrderByDisplayIndexAsc(
                                        agenda.getId()))
                        .willReturn(List.of(low, proposed));
                given(voteRepository.findAllByAgendaItemId(agenda.getId()))
                        .willReturn(List.of(myVote, jieunVote));

                AgendaDetailResponse result =
                        agendaService.getAgenda(ROOM_ID, agenda.getId(), USER_ID);

                assertThat(result.votedCount()).isEqualTo(2);
                assertThat(result.myOptionId()).isEqualTo(low.getId());
                assertThat(result.eligible()).isTrue();
                assertThat(result.options())
                        .extracting(OptionResult::optionId)
                        .containsExactly(low.getId(), proposed.getId());
                OptionResult first = result.options().getFirst();
                assertThat(first.voteCount()).isEqualTo(2);
                assertThat(first.voters())
                        .containsExactlyInAnyOrder(
                                new MemberRef(jieun.getId(), "지은"),
                                new MemberRef(me.getId(), "민수"));
                assertThat(first.proposedBy()).isNull();
                OptionResult second = result.options().get(1);
                assertThat(second.voteCount()).isZero();
                assertThat(second.voters()).isEmpty();
                assertThat(second.proposedBy()).isEqualTo(new MemberRef(jieun.getId(), "지은"));
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("방의 활동 멤버가 아니면 멤버 확인에서 던진 에러를 그대로 돌려주고 안건을 조회하지 않는다")
            void propagatesMemberError() {
                given(roomMemberService.getActiveMember(ROOM_ID, USER_ID))
                        .willThrow(new BusinessException(CommonErrorCode.FORBIDDEN));

                assertThatThrownBy(() -> agendaService.getAgendas(ROOM_ID, USER_ID))
                        .isInstanceOf(BusinessException.class)
                        .extracting("errorCode")
                        .isEqualTo(CommonErrorCode.FORBIDDEN);
                assertThatThrownBy(() -> agendaService.getAgenda(ROOM_ID, 5L, USER_ID))
                        .isInstanceOf(BusinessException.class)
                        .extracting("errorCode")
                        .isEqualTo(CommonErrorCode.FORBIDDEN);
                verifyNoInteractions(agendaItemRepository);
            }

            @Test
            @DisplayName("없거나 다른 방의 안건을 상세 조회하면 AGENDA_NOT_FOUND를 던진다")
            void rejectsAgendaOfOtherRoom() {
                givenActiveMember();
                given(agendaItemRepository.findByIdAndRoomId(5L, ROOM_ID))
                        .willReturn(Optional.empty());

                assertThatThrownBy(() -> agendaService.getAgenda(ROOM_ID, 5L, USER_ID))
                        .isInstanceOf(BusinessException.class)
                        .extracting("errorCode")
                        .isEqualTo(AgendaErrorCode.AGENDA_NOT_FOUND);
            }

            @Test
            @DisplayName("없거나 다른 방의 안건 코멘트를 조회하면 AGENDA_NOT_FOUND를 던지고 코멘트를 읽지 않는다")
            void rejectsCommentsOfOtherRoomAgenda() {
                givenActiveMember();
                given(agendaItemRepository.findByIdAndRoomId(5L, ROOM_ID))
                        .willReturn(Optional.empty());

                assertThatThrownBy(() -> agendaService.getComments(ROOM_ID, 5L, USER_ID))
                        .isInstanceOf(BusinessException.class)
                        .extracting("errorCode")
                        .isEqualTo(AgendaErrorCode.AGENDA_NOT_FOUND);
                verifyNoInteractions(commentService);
            }
        }
    }

    @Nested
    class Create {

        private AgendaItem givenAgendaInRoom(AgendaItem agenda) {
            given(agendaItemRepository.findByIdAndRoomId(agenda.getId(), ROOM_ID))
                    .willReturn(Optional.of(agenda));
            return agenda;
        }

        private void roomIs(RoomStatus status) {
            ReflectionTestUtils.setField(room, "status", status);
        }

        private CommentResponse addComment(AgendaItem agenda, String body) {
            return agendaService.addComment(
                    ROOM_ID, agenda.getId(), USER_ID, new CommentRequest(body));
        }

        @Nested
        class Success {

            @ParameterizedTest
            @EnumSource(
                    value = RoomStatus.class,
                    names = {"SURVEYING", "PLANNING", "REVIEWING"})
            @DisplayName("확정 전 단계에서는 안건 코멘트를 저장하고 방 잠금은 잡지 않는다")
            void savesCommentWithoutLock(RoomStatus status) {
                roomIs(status);
                givenActiveMember();
                AgendaItem agenda = givenAgendaInRoom(votingAgenda(NOW.plusDays(3)));
                Comment saved = withId(Comment.onAgenda(agenda, me, "150만 원은 부담돼요"));
                ReflectionTestUtils.setField(saved, "createdAt", NOW);
                given(commentService.addOnAgenda(agenda, me, "150만 원은 부담돼요")).willReturn(saved);

                CommentResponse response = addComment(agenda, "150만 원은 부담돼요");

                assertThat(response)
                        .isEqualTo(
                                new CommentResponse(
                                        saved.getId(), me.getId(), "민수", "150만 원은 부담돼요", NOW));
                verifyNoInteractions(roomService);
            }

            @Test
            @DisplayName("이미 결정된 안건에도 코멘트를 남길 수 있다")
            void savesCommentOnResolvedAgenda() {
                givenActiveMember();
                AgendaItem agenda = votingAgenda(NOW.plusDays(3));
                agenda.resolve(budget(agenda, 600_000L, 0, null), NOW);
                givenAgendaInRoom(agenda);
                Comment saved = withId(Comment.onAgenda(agenda, me, "좋아요"));
                given(commentService.addOnAgenda(agenda, me, "좋아요")).willReturn(saved);

                assertThat(addComment(agenda, "좋아요").body()).isEqualTo("좋아요");
            }
        }

        @Nested
        class Exception {

            @ParameterizedTest
            @EnumSource(
                    value = RoomStatus.class,
                    names = {"FINALIZED", "EXPIRED"})
            @DisplayName("확정됐거나 만료된 방에서는 INVALID_STATUS를 던지고 안건과 코멘트를 건드리지 않는다")
            void rejectsClosedRoom(RoomStatus status) {
                roomIs(status);
                givenActiveMember();

                assertThatThrownBy(() ->
                                agendaService.addComment(
                                        ROOM_ID, 5L, USER_ID, new CommentRequest("늦었나요")))
                        .isInstanceOf(BusinessException.class)
                        .extracting("errorCode")
                        .isEqualTo(CommonErrorCode.INVALID_STATUS);
                verifyNoInteractions(agendaItemRepository, commentService);
            }

            @Test
            @DisplayName("없거나 다른 방의 안건에 코멘트를 남기면 AGENDA_NOT_FOUND를 던지고 저장하지 않는다")
            void rejectsAgendaOfOtherRoom() {
                givenActiveMember();
                given(agendaItemRepository.findByIdAndRoomId(5L, ROOM_ID))
                        .willReturn(Optional.empty());

                assertThatThrownBy(() ->
                                agendaService.addComment(
                                        ROOM_ID, 5L, USER_ID, new CommentRequest("여기 맞나요")))
                        .isInstanceOf(BusinessException.class)
                        .extracting("errorCode")
                        .isEqualTo(AgendaErrorCode.AGENDA_NOT_FOUND);
                verifyNoInteractions(commentService);
            }
        }
    }

    @Nested
    class Update {

        @BeforeEach
        void giveRoomId() {
            ReflectionTestUtils.setField(room, "id", ROOM_ID);
        }

        private AgendaItem openAgenda(AgendaSubject subject, int totalVoter) {
            AgendaItem agenda =
                    withId(
                            AgendaItem.create(
                                    room,
                                    subject,
                                    1,
                                    "안건 제목",
                                    totalVoter,
                                    NOW.minusHours(1),
                                    NOW.plusDays(3)));
            ReflectionTestUtils.setField(agenda, "createdAt", NOW.minusHours(1));
            return agenda;
        }

        // 만장일치로 이미 결정된 설문 항목
        private AgendaItem resolvedAgenda(AgendaSubject subject) {
            AgendaItem agenda = openAgenda(subject, 2);
            AgendaOption option =
                    withId(
                            switch (subject) {
                                case STAY -> AgendaOption.stay(agenda, StayType.HOTEL, 0);
                                case PACE -> AgendaOption.pace(agenda, Pace.RELAXED, 0);
                                default -> throw new IllegalArgumentException(subject.name());
                            });
            agenda.resolve(option, NOW);
            return agenda;
        }

        private AgendaOption finalOption(AgendaItem agenda, FinalPlanChoice choice) {
            return withId(AgendaOption.finalPlan(agenda, choice));
        }

        private void givenVotes(AgendaItem agenda, Vote... votes) {
            given(voteRepository.findAllByAgendaItemId(agenda.getId())).willReturn(List.of(votes));
        }

        // allMatch가 앞 주제에서 멈추면 뒤 주제는 조회하지 않으므로 lenient로 둔다
        private void givenLatestRounds(AgendaItem... latest) {
            for (AgendaItem agenda : latest) {
                lenient()
                        .when(
                                agendaItemRepository.findTopByRoomIdAndSubjectOrderByRoundDesc(
                                        ROOM_ID, agenda.getSubject()))
                        .thenReturn(Optional.of(agenda));
            }
        }

        private void reviewing() {
            ReflectionTestUtils.setField(room, "status", RoomStatus.REVIEWING);
        }

        // 투표(V-03)의 앞부분: 지금 시각, 방 잠금, 멤버 확인
        private void givenLockedRoomAndMember(RoomMember member) {
            given(clock.instant()).willReturn(NOW.atZone(SEOUL).toInstant());
            given(clock.getZone()).willReturn(SEOUL);
            given(roomService.getForUpdate(ROOM_ID)).willReturn(room);
            given(roomMemberService.getActiveMember(ROOM_ID, USER_ID)).willReturn(member);
        }

        private void givenAgenda(AgendaItem agenda) {
            given(agendaItemRepository.findByIdAndRoomId(agenda.getId(), ROOM_ID))
                    .willReturn(Optional.of(agenda));
        }

        private void givenOption(AgendaItem agenda, AgendaOption option) {
            given(agendaOptionRepository.findByIdAndAgendaItemId(option.getId(), agenda.getId()))
                    .willReturn(Optional.of(option));
        }

        private void givenMyVote(AgendaItem agenda, Vote mine) {
            given(voteRepository.findByAgendaItemIdAndMemberId(agenda.getId(), me.getId()))
                    .willReturn(Optional.ofNullable(mine));
        }

        private AgendaDetailResponse voteFor(
                AgendaItem agenda, AgendaOption option, String comment) {
            return agendaService.vote(
                    ROOM_ID, agenda.getId(), USER_ID, new VoteRequest(option.getId(), comment));
        }

        private void assertVoteError(Runnable action, ErrorCode errorCode) {
            assertThatThrownBy(action::run)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(errorCode);
        }

        @Nested
        class Success {

            @Test
            @DisplayName("처음 투표하면 내 표를 저장하고 아직 남은 자격자가 있으면 투표 중으로 남는다")
            void savesFirstVote() {
                AgendaItem budget = openAgenda(AgendaSubject.BUDGET, 2);
                AgendaOption low = budget(budget, 600_000L, 0, null);
                givenLockedRoomAndMember(me);
                givenAgenda(budget);
                givenOption(budget, low);
                givenMyVote(budget, null);
                givenVotes(budget, vote(budget, low, me));

                AgendaDetailResponse response = voteFor(budget, low, null);

                verify(voteRepository).save(argThat(v -> v.getMember() == me && v.isFor(low)));
                assertThat(response.status()).isEqualTo(AgendaStatus.VOTING);
                assertThat(response.votedCount()).isEqualTo(1);
                assertThat(response.myOptionId()).isEqualTo(low.getId());
                verifyNoInteractions(commentService);
            }

            @Test
            @DisplayName("이미 투표했으면 새 표를 만들지 않고 내 표의 선지만 바꾼다")
            void changesExistingVote() {
                AgendaItem budget = openAgenda(AgendaSubject.BUDGET, 2);
                AgendaOption low = budget(budget, 600_000L, 0, null);
                AgendaOption high = budget(budget, 1_000_000L, 1, null);
                Vote mine = vote(budget, low, me);
                givenLockedRoomAndMember(me);
                givenAgenda(budget);
                givenOption(budget, high);
                givenMyVote(budget, mine);
                givenVotes(budget, mine);

                voteFor(budget, high, null);

                assertThat(mine.isFor(high)).isTrue();
                verify(voteRepository, never()).save(any());
            }

            @Test
            @DisplayName("마지막 표로 전원이 같은 선지가 되면 응답의 안건 상태가 RESOLVED다")
            void resolvesOnLastVote() {
                AgendaItem budget = openAgenda(AgendaSubject.BUDGET, 2);
                AgendaOption low = budget(budget, 600_000L, 0, null);
                givenLockedRoomAndMember(me);
                givenAgenda(budget);
                givenOption(budget, low);
                givenMyVote(budget, null);
                givenVotes(budget, vote(budget, low, jieun), vote(budget, low, me));
                givenLatestRounds(budget, openAgenda(AgendaSubject.STAY, 2));

                AgendaDetailResponse response = voteFor(budget, low, null);

                assertThat(response.status()).isEqualTo(AgendaStatus.RESOLVED);
                assertThat(budget.getResolvedOptionId()).isEqualTo(low.getId());
            }

            @Test
            @DisplayName("최종 확정에 이유와 함께 반대하면 코멘트를 남기고 안건이 반대로 끝나 방이 PLANNING으로 돌아간다")
            void rejectsFinalPlanWithComment() {
                reviewing();
                AgendaItem finalPlan = openAgenda(AgendaSubject.FINAL_PLAN, 3);
                AgendaOption reject = finalOption(finalPlan, FinalPlanChoice.REJECT);
                givenLockedRoomAndMember(me);
                givenAgenda(finalPlan);
                givenOption(finalPlan, reject);
                givenMyVote(finalPlan, null);
                givenVotes(finalPlan, vote(finalPlan, reject, me));

                AgendaDetailResponse response = voteFor(finalPlan, reject, "일정이 너무 빡빡해요");

                verify(commentService).addOnAgenda(finalPlan, me, "일정이 너무 빡빡해요");
                assertThat(response.status()).isEqualTo(AgendaStatus.RESOLVED);
                assertThat(room.getStatus()).isEqualTo(RoomStatus.PLANNING);
            }

            @Test
            @DisplayName("마지막 표로 전원이 같은 선지를 고르면 설문 안건이 결정되고 다른 항목이 투표 중이면 방은 SURVEYING에 머문다")
            void resolvesSurveyAgendaOnLastVote() {
                AgendaItem budget = openAgenda(AgendaSubject.BUDGET, 2);
                AgendaOption low = budget(budget, 600_000L, 0, null);
                givenVotes(budget, vote(budget, low, me), vote(budget, low, jieun));
                givenLatestRounds(
                        budget,
                        openAgenda(AgendaSubject.STAY, 2),
                        resolvedAgenda(AgendaSubject.PACE));

                boolean resolved = agendaService.judge(room, budget, NOW);

                assertThat(resolved).isTrue();
                assertThat(budget.getStatus()).isEqualTo(AgendaStatus.RESOLVED);
                assertThat(budget.getResolvedOptionId()).isEqualTo(low.getId());
                assertThat(budget.getResolvedAt()).isEqualTo(NOW);
                assertThat(room.getStatus()).isEqualTo(RoomStatus.SURVEYING);
            }

            @Test
            @DisplayName("예산·숙소·이동 강도의 최신 회차가 모두 결정되면 방이 PLANNING으로 넘어간다")
            void startsPlanningWhenAllSurveySubjectsResolved() {
                AgendaItem budget = openAgenda(AgendaSubject.BUDGET, 2);
                AgendaOption low = budget(budget, 600_000L, 0, null);
                givenVotes(budget, vote(budget, low, me), vote(budget, low, jieun));
                givenLatestRounds(
                        budget,
                        resolvedAgenda(AgendaSubject.STAY),
                        resolvedAgenda(AgendaSubject.PACE));

                agendaService.judge(room, budget, NOW);

                assertThat(room.getStatus()).isEqualTo(RoomStatus.PLANNING);
            }

            @Test
            @DisplayName("최종 확정에 자격자 전원이 동의하면 방이 FINALIZED가 되고 확정 시각이 기록된다")
            void finalizesRoomWhenAllAgreed() {
                reviewing();
                AgendaItem finalPlan = openAgenda(AgendaSubject.FINAL_PLAN, 2);
                AgendaOption agree = finalOption(finalPlan, FinalPlanChoice.AGREE);
                givenVotes(finalPlan, vote(finalPlan, agree, me), vote(finalPlan, agree, jieun));

                boolean resolved = agendaService.judge(room, finalPlan, NOW);

                assertThat(resolved).isTrue();
                assertThat(finalPlan.getResolvedOptionId()).isEqualTo(agree.getId());
                assertThat(room.getStatus()).isEqualTo(RoomStatus.FINALIZED);
                assertThat(room.getFinalizedAt()).isEqualTo(NOW);
            }

            @Test
            @DisplayName("최종 확정에 반대 표가 들어오면 미투표자가 있어도 반대로 결정되고 방이 PLANNING으로 돌아간다")
            void returnsToPlanningOnReject() {
                reviewing();
                AgendaItem finalPlan = openAgenda(AgendaSubject.FINAL_PLAN, 3);
                AgendaOption reject = finalOption(finalPlan, FinalPlanChoice.REJECT);
                givenVotes(finalPlan, vote(finalPlan, reject, me));

                boolean resolved = agendaService.judge(room, finalPlan, NOW);

                assertThat(resolved).isTrue();
                assertThat(finalPlan.getStatus()).isEqualTo(AgendaStatus.RESOLVED);
                assertThat(finalPlan.getResolvedOptionId()).isEqualTo(reject.getId());
                assertThat(room.getStatus()).isEqualTo(RoomStatus.PLANNING);
                assertThat(room.getFinalizedAt()).isNull();
            }

            @Test
            @DisplayName("멤버가 나가 남은 표가 자격자 수와 같아지고 모두 같은 선지면 안건이 결정된다")
            void resolvesWhenMemberLeaves() {
                AgendaItem budget = openAgenda(AgendaSubject.BUDGET, 3);
                AgendaOption low = budget(budget, 600_000L, 0, null);
                // R-06: 나간 멤버의 표는 지워졌고 남은 두 표가 모두 같은 선지다
                givenVotes(budget, vote(budget, low, me), vote(budget, low, jieun));
                givenLatestRounds(budget, openAgenda(AgendaSubject.STAY, 2));

                budget.removeVoter();
                boolean resolved = agendaService.judge(room, budget, NOW);

                assertThat(resolved).isTrue();
                assertThat(budget.getResolvedOptionId()).isEqualTo(low.getId());
            }

            @Test
            @DisplayName("최종 확정 투표 중 미투표자가 나가고 남은 사람이 모두 동의했으면 방이 FINALIZED가 된다")
            void finalizesWhenNonVoterLeavesFinalPlan() {
                reviewing();
                AgendaItem finalPlan = openAgenda(AgendaSubject.FINAL_PLAN, 3);
                AgendaOption agree = finalOption(finalPlan, FinalPlanChoice.AGREE);
                givenVotes(finalPlan, vote(finalPlan, agree, me), vote(finalPlan, agree, jieun));

                finalPlan.removeVoter();
                boolean resolved = agendaService.judge(room, finalPlan, NOW);

                assertThat(resolved).isTrue();
                assertThat(room.getStatus()).isEqualTo(RoomStatus.FINALIZED);
            }

            @Test
            @DisplayName("최종 확정 안건이 미결 마감되면 방이 PLANNING으로 돌아간다")
            void returnsToPlanningWhenFinalPlanExpired() {
                reviewing();
                AgendaItem finalPlan = openAgenda(AgendaSubject.FINAL_PLAN, 2);
                finalPlan.expire();

                agendaService.applyExpired(room, finalPlan);

                assertThat(room.getStatus()).isEqualTo(RoomStatus.PLANNING);
            }

            @Test
            @DisplayName("설문 안건이 미결 마감되면 다음 회차를 기다리며 방은 SURVEYING에 머문다")
            void staysSurveyingWhenSurveyAgendaExpired() {
                AgendaItem budget = openAgenda(AgendaSubject.BUDGET, 2);
                budget.expire();

                agendaService.applyExpired(room, budget);

                assertThat(room.getStatus()).isEqualTo(RoomStatus.SURVEYING);
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("방이 없으면 ROOM_NOT_FOUND를 던지고 시각을 구하거나 멤버를 확인하지 않는다")
            void rejectsMissingRoom() {
                given(roomService.getForUpdate(ROOM_ID))
                        .willThrow(new BusinessException(RoomErrorCode.ROOM_NOT_FOUND));

                assertVoteError(
                        () -> agendaService.vote(ROOM_ID, 5L, USER_ID, new VoteRequest(1L, null)),
                        RoomErrorCode.ROOM_NOT_FOUND);
                // 시각은 잠금을 얻은 뒤에 구한다
                verifyNoInteractions(clock, roomMemberService, voteRepository);
            }

            @Test
            @DisplayName("방이 SURVEYING·REVIEWING이 아니면 INVALID_STATUS를 던지고 안건을 조회하지 않는다")
            void rejectsInvalidRoomStatus() {
                ReflectionTestUtils.setField(room, "status", RoomStatus.PLANNING);
                givenLockedRoomAndMember(me);

                assertVoteError(
                        () -> agendaService.vote(ROOM_ID, 5L, USER_ID, new VoteRequest(1L, null)),
                        CommonErrorCode.INVALID_STATUS);
                verifyNoInteractions(agendaItemRepository, voteRepository);
            }

            @Test
            @DisplayName("없거나 다른 방의 안건이면 AGENDA_NOT_FOUND를 던진다")
            void rejectsAgendaOfOtherRoom() {
                givenLockedRoomAndMember(me);
                given(agendaItemRepository.findByIdAndRoomId(5L, ROOM_ID))
                        .willReturn(Optional.empty());

                assertVoteError(
                        () -> agendaService.vote(ROOM_ID, 5L, USER_ID, new VoteRequest(1L, null)),
                        AgendaErrorCode.AGENDA_NOT_FOUND);
            }

            @Test
            @DisplayName("마감 시각이 지난 안건이면 AGENDA_CLOSED를 던진다")
            void rejectsClosedAgenda() {
                AgendaItem closed =
                        withId(
                                AgendaItem.create(
                                        room,
                                        AgendaSubject.BUDGET,
                                        1,
                                        "안건 제목",
                                        2,
                                        NOW.minusDays(2),
                                        NOW.minusMinutes(1)));
                AgendaOption low = budget(closed, 600_000L, 0, null);
                givenLockedRoomAndMember(me);
                givenAgenda(closed);

                assertVoteError(() -> voteFor(closed, low, null), AgendaErrorCode.AGENDA_CLOSED);
                verifyNoInteractions(voteRepository);
            }

            @Test
            @DisplayName("안건이 열린 뒤에 합류한 멤버면 NOT_ELIGIBLE_VOTER를 던진다")
            void rejectsLateMember() {
                AgendaItem budget = openAgenda(AgendaSubject.BUDGET, 2);
                AgendaOption low = budget(budget, 600_000L, 0, null);
                // 안건은 NOW - 1시간에 열렸다
                givenLockedRoomAndMember(member("현우", NOW));
                givenAgenda(budget);

                assertVoteError(
                        () -> voteFor(budget, low, null), AgendaErrorCode.NOT_ELIGIBLE_VOTER);
                verifyNoInteractions(voteRepository);
            }

            @Test
            @DisplayName("이 안건의 선택지가 아니면 OPTION_NOT_IN_AGENDA를 던진다")
            void rejectsOptionOfOtherAgenda() {
                AgendaItem budget = openAgenda(AgendaSubject.BUDGET, 2);
                givenLockedRoomAndMember(me);
                givenAgenda(budget);
                given(agendaOptionRepository.findByIdAndAgendaItemId(99L, budget.getId()))
                        .willReturn(Optional.empty());

                assertVoteError(
                        () ->
                                agendaService.vote(
                                        ROOM_ID,
                                        budget.getId(),
                                        USER_ID,
                                        new VoteRequest(99L, null)),
                        AgendaErrorCode.OPTION_NOT_IN_AGENDA);
                verifyNoInteractions(voteRepository);
            }

            @Test
            @DisplayName("최종 확정에 이유 없이 반대하면 REJECT_COMMENT_REQUIRED를 던지고 표와 코멘트를 남기지 않는다")
            void rejectsRejectWithoutComment() {
                reviewing();
                AgendaItem finalPlan = openAgenda(AgendaSubject.FINAL_PLAN, 2);
                AgendaOption reject = finalOption(finalPlan, FinalPlanChoice.REJECT);
                givenLockedRoomAndMember(me);
                givenAgenda(finalPlan);
                givenOption(finalPlan, reject);

                assertVoteError(
                        () -> voteFor(finalPlan, reject, "   "),
                        AgendaErrorCode.REJECT_COMMENT_REQUIRED);
                verifyNoInteractions(voteRepository, commentService);
                assertThat(finalPlan.getStatus()).isEqualTo(AgendaStatus.VOTING);
            }

            @Test
            @DisplayName("자격자 전원이 투표했어도 표가 갈리면 안건과 방 상태가 그대로다")
            void keepsVotingWhenSplit() {
                AgendaItem budget = openAgenda(AgendaSubject.BUDGET, 2);
                AgendaOption low = budget(budget, 600_000L, 0, null);
                AgendaOption high = budget(budget, 1_000_000L, 1, null);
                givenVotes(budget, vote(budget, low, me), vote(budget, high, jieun));

                boolean resolved = agendaService.judge(room, budget, NOW);

                assertThat(resolved).isFalse();
                assertThat(budget.getStatus()).isEqualTo(AgendaStatus.VOTING);
                assertThat(budget.getResolvedOptionId()).isNull();
                assertThat(room.getStatus()).isEqualTo(RoomStatus.SURVEYING);
                verifyNoInteractions(agendaItemRepository);
            }

            @Test
            @DisplayName("미결 마감되지 않은 안건으로 마감 반영을 부르면 코드 버그라 예외를 던진다")
            void rejectsApplyExpiredOnVotingAgenda() {
                reviewing();
                AgendaItem finalPlan = openAgenda(AgendaSubject.FINAL_PLAN, 2);

                assertThatThrownBy(() -> agendaService.applyExpired(room, finalPlan))
                        .isInstanceOf(IllegalStateException.class);
                assertThat(room.getStatus()).isEqualTo(RoomStatus.REVIEWING);
            }
        }
    }

    @Nested
    class AddOption {

        @BeforeEach
        void giveRoomId() {
            ReflectionTestUtils.setField(room, "id", ROOM_ID);
        }

        // NOW - 1시간에 열린 투표 중 안건
        private AgendaItem openAgenda(AgendaSubject subject) {
            return agenda(subject, NOW.minusHours(1), NOW.plusDays(3));
        }

        private void givenLockedRoomAndMember(RoomMember member) {
            given(clock.instant()).willReturn(NOW.atZone(SEOUL).toInstant());
            given(clock.getZone()).willReturn(SEOUL);
            given(roomService.getForUpdate(ROOM_ID)).willReturn(room);
            given(roomMemberService.getActiveMember(ROOM_ID, USER_ID)).willReturn(member);
        }

        private void givenAgenda(AgendaItem agenda) {
            given(agendaItemRepository.findByIdAndRoomId(agenda.getId(), ROOM_ID))
                    .willReturn(Optional.of(agenda));
        }

        private void givenOptions(AgendaItem agenda, AgendaOption... options) {
            given(
                            agendaOptionRepository.findAllByAgendaItemIdOrderByDisplayIndexAsc(
                                    agenda.getId()))
                    .willReturn(List.of(options));
        }

        // 선지 수·중복 검사를 통과하는 상태
        private void givenAddable(AgendaItem agenda, String roundedValue) {
            given(
                            agendaOptionRepository.existsByAgendaItemIdAndProposedById(
                                    agenda.getId(), me.getId()))
                    .willReturn(false);
            given(agendaOptionRepository.findByAgendaItemIdAndValue(agenda.getId(), roundedValue))
                    .willReturn(Optional.empty());
        }

        private AgendaDetailResponse add(AgendaItem agenda, long amount) {
            return agendaService.addBudgetOption(
                    ROOM_ID, agenda.getId(), USER_ID, new AddOptionRequest(amount));
        }

        private void assertError(Runnable action, ErrorCode errorCode) {
            assertThatThrownBy(action::run)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(errorCode);
        }

        @Nested
        class Success {

            @Test
            @DisplayName("금액을 만원 단위로 반올림해 내가 올린 선지로 맨 뒤에 저장하고 판정은 하지 않는다")
            void savesRoundedOptionAtEnd() {
                AgendaItem budget = openAgenda(AgendaSubject.BUDGET);
                AgendaOption low = budget(budget, 600_000L, 0, null);
                AgendaOption high = budget(budget, 1_000_000L, 1, null);
                givenLockedRoomAndMember(me);
                givenAgenda(budget);
                givenAddable(budget, "1230000");
                givenOptions(budget, low, high);

                add(budget, 1_234_567L);

                verify(agendaOptionRepository)
                        .saveAndFlush(
                                argThat(o ->
                                        o.getValue().equals("1230000")
                                                && o.getLabel().equals("123만원")
                                                && o.getDisplayIndex() == 2
                                                && o.isProposedBy(me)
                                                && o.belongsTo(budget)));
                assertThat(budget.getStatus()).isEqualTo(AgendaStatus.VOTING);
                verify(agendaItemRepository, never())
                        .findTopByRoomIdAndSubjectOrderByRoundDesc(any(), any());
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("방이 SURVEYING이 아니면 INVALID_STATUS를 던지고 안건을 조회하지 않는다")
            void rejectsInvalidRoomStatus() {
                ReflectionTestUtils.setField(room, "status", RoomStatus.PLANNING);
                givenLockedRoomAndMember(me);

                assertError(
                        () ->
                                agendaService.addBudgetOption(
                                        ROOM_ID, 5L, USER_ID, new AddOptionRequest(1_000_000L)),
                        CommonErrorCode.INVALID_STATUS);
                verifyNoInteractions(agendaItemRepository, agendaOptionRepository);
            }

            @Test
            @DisplayName("없거나 다른 방의 안건이면 AGENDA_NOT_FOUND를 던진다")
            void rejectsAgendaOfOtherRoom() {
                givenLockedRoomAndMember(me);
                given(agendaItemRepository.findByIdAndRoomId(5L, ROOM_ID))
                        .willReturn(Optional.empty());

                assertError(
                        () ->
                                agendaService.addBudgetOption(
                                        ROOM_ID, 5L, USER_ID, new AddOptionRequest(1_000_000L)),
                        AgendaErrorCode.AGENDA_NOT_FOUND);
                verifyNoInteractions(agendaOptionRepository);
            }

            @Test
            @DisplayName("예산 안건이 아니면 OPTION_NOT_ADDABLE을 던진다")
            void rejectsNonBudgetAgenda() {
                AgendaItem stay = openAgenda(AgendaSubject.STAY);
                givenLockedRoomAndMember(me);
                givenAgenda(stay);

                assertError(() -> add(stay, 1_000_000L), AgendaErrorCode.OPTION_NOT_ADDABLE);
                verifyNoInteractions(agendaOptionRepository);
            }

            @Test
            @DisplayName("마감 시각이 지난 안건이면 AGENDA_CLOSED를 던진다")
            void rejectsClosedAgenda() {
                AgendaItem closed =
                        agenda(AgendaSubject.BUDGET, NOW.minusDays(2), NOW.minusMinutes(1));
                givenLockedRoomAndMember(me);
                givenAgenda(closed);

                assertError(() -> add(closed, 1_000_000L), AgendaErrorCode.AGENDA_CLOSED);
                verifyNoInteractions(agendaOptionRepository);
            }

            @Test
            @DisplayName("안건이 열린 뒤에 합류한 멤버면 NOT_ELIGIBLE_VOTER를 던진다")
            void rejectsLateMember() {
                AgendaItem budget = openAgenda(AgendaSubject.BUDGET);
                givenLockedRoomAndMember(member("현우", NOW));
                givenAgenda(budget);

                assertError(() -> add(budget, 1_000_000L), AgendaErrorCode.NOT_ELIGIBLE_VOTER);
                verifyNoInteractions(agendaOptionRepository);
            }

            @Test
            @DisplayName("이 회차에 이미 선지를 추가했으면 OPTION_ADD_LIMIT_EXCEEDED를 던지고 저장하지 않는다")
            void rejectsSecondOptionOfSameMember() {
                AgendaItem budget = openAgenda(AgendaSubject.BUDGET);
                givenLockedRoomAndMember(me);
                givenAgenda(budget);
                given(
                                agendaOptionRepository.existsByAgendaItemIdAndProposedById(
                                        budget.getId(), me.getId()))
                        .willReturn(true);

                assertError(
                        () -> add(budget, 1_000_000L), AgendaErrorCode.OPTION_ADD_LIMIT_EXCEEDED);
                verify(agendaOptionRepository, never()).saveAndFlush(any());
            }

            @Test
            @DisplayName("반올림한 금액이 기존 선지와 같으면 OPTION_ALREADY_EXISTS를 던지고 data에 기존 선지 id를 담는다")
            void rejectsDuplicateAfterRounding() {
                AgendaItem budget = openAgenda(AgendaSubject.BUDGET);
                AgendaOption existing = budget(budget, 1_230_000L, 0, null);
                givenLockedRoomAndMember(me);
                givenAgenda(budget);
                given(
                                agendaOptionRepository.existsByAgendaItemIdAndProposedById(
                                        budget.getId(), me.getId()))
                        .willReturn(false);
                given(agendaOptionRepository.findByAgendaItemIdAndValue(budget.getId(), "1230000"))
                        .willReturn(Optional.of(existing));

                assertThatThrownBy(() -> add(budget, 1_226_000L))
                        .isInstanceOf(BusinessException.class)
                        .satisfies(e -> {
                            BusinessException be = (BusinessException) e;
                            assertThat(be.getErrorCode())
                                    .isEqualTo(AgendaErrorCode.OPTION_ALREADY_EXISTS);
                            assertThat(be.getData())
                                    .isEqualTo(Map.of("optionId", existing.getId()));
                        });
                verify(agendaOptionRepository, never()).saveAndFlush(any());
            }

            @Test
            @DisplayName("저장 중 같은 금액의 유니크 제약에 걸리면 OPTION_ALREADY_EXISTS로 바꿔 던진다")
            void convertsUniqueViolation() {
                AgendaItem budget = openAgenda(AgendaSubject.BUDGET);
                givenLockedRoomAndMember(me);
                givenAgenda(budget);
                givenAddable(budget, "1000000");
                givenOptions(budget);
                given(agendaOptionRepository.saveAndFlush(any()))
                        .willThrow(new DataIntegrityViolationException("uk_agenda_option"));

                assertError(() -> add(budget, 1_000_000L), AgendaErrorCode.OPTION_ALREADY_EXISTS);
            }
        }
    }

    @Nested
    class DeleteOption {

        @BeforeEach
        void giveRoomId() {
            ReflectionTestUtils.setField(room, "id", ROOM_ID);
        }

        // NOW - 1시간에 열린 투표 중 예산 안건
        private AgendaItem openBudget() {
            return agenda(AgendaSubject.BUDGET, NOW.minusHours(1), NOW.plusDays(3));
        }

        private void givenLockedRoomAndMember(RoomMember member) {
            given(clock.instant()).willReturn(NOW.atZone(SEOUL).toInstant());
            given(clock.getZone()).willReturn(SEOUL);
            given(roomService.getForUpdate(ROOM_ID)).willReturn(room);
            given(roomMemberService.getActiveMember(ROOM_ID, USER_ID)).willReturn(member);
        }

        private void givenAgenda(AgendaItem agenda) {
            given(agendaItemRepository.findByIdAndRoomId(agenda.getId(), ROOM_ID))
                    .willReturn(Optional.of(agenda));
        }

        private void givenOption(AgendaItem agenda, AgendaOption option) {
            given(agendaOptionRepository.findByIdAndAgendaItemId(option.getId(), agenda.getId()))
                    .willReturn(Optional.of(option));
        }

        private void givenOthersVoteCount(AgendaOption option, long count) {
            given(voteRepository.countByAgendaOptionIdAndMemberIdNot(option.getId(), me.getId()))
                    .willReturn(count);
        }

        private AgendaDetailResponse delete(AgendaItem agenda, AgendaOption option) {
            return agendaService.deleteBudgetOption(
                    ROOM_ID, agenda.getId(), option.getId(), USER_ID);
        }

        private void assertError(Runnable action, ErrorCode errorCode) {
            assertThatThrownBy(action::run)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(errorCode);
        }

        // 아무것도 지우지 않았는지
        private void verifyNothingDeleted() {
            verify(voteRepository, never()).deleteByAgendaOptionIdAndMemberId(any(), any());
            verify(agendaOptionRepository, never()).delete(any());
        }

        @Nested
        class Success {

            @Test
            @DisplayName("내가 올린 선지에 다른 사람의 표가 없으면 내 표를 먼저 지운 뒤 선지를 지우고 안건은 투표 중으로 남는다")
            void deletesMyVoteThenOption() {
                AgendaItem budget = openBudget();
                AgendaOption mine = budget(budget, 1_200_000L, 1, me);
                givenLockedRoomAndMember(me);
                givenAgenda(budget);
                givenOption(budget, mine);
                givenOthersVoteCount(mine, 0);

                AgendaDetailResponse response = delete(budget, mine);

                InOrder order = inOrder(voteRepository, agendaOptionRepository);
                order.verify(voteRepository)
                        .deleteByAgendaOptionIdAndMemberId(mine.getId(), me.getId());
                order.verify(agendaOptionRepository).delete(mine);
                assertThat(response.status()).isEqualTo(AgendaStatus.VOTING);
                verify(agendaItemRepository, never())
                        .findTopByRoomIdAndSubjectOrderByRoundDesc(any(), any());
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("방이 SURVEYING이 아니면 INVALID_STATUS를 던지고 안건을 조회하지 않는다")
            void rejectsInvalidRoomStatus() {
                ReflectionTestUtils.setField(room, "status", RoomStatus.PLANNING);
                givenLockedRoomAndMember(me);

                assertError(
                        () -> agendaService.deleteBudgetOption(ROOM_ID, 5L, 9L, USER_ID),
                        CommonErrorCode.INVALID_STATUS);
                verifyNoInteractions(agendaItemRepository, agendaOptionRepository, voteRepository);
            }

            @Test
            @DisplayName("없거나 다른 방의 안건이면 AGENDA_NOT_FOUND를 던진다")
            void rejectsAgendaOfOtherRoom() {
                givenLockedRoomAndMember(me);
                given(agendaItemRepository.findByIdAndRoomId(5L, ROOM_ID))
                        .willReturn(Optional.empty());

                assertError(
                        () -> agendaService.deleteBudgetOption(ROOM_ID, 5L, 9L, USER_ID),
                        AgendaErrorCode.AGENDA_NOT_FOUND);
                verifyNoInteractions(agendaOptionRepository, voteRepository);
            }

            @Test
            @DisplayName("마감 시각이 지난 안건이면 AGENDA_CLOSED를 던진다")
            void rejectsClosedAgenda() {
                AgendaItem closed =
                        agenda(AgendaSubject.BUDGET, NOW.minusDays(2), NOW.minusMinutes(1));
                AgendaOption mine = budget(closed, 1_200_000L, 1, me);
                givenLockedRoomAndMember(me);
                givenAgenda(closed);

                assertError(() -> delete(closed, mine), AgendaErrorCode.AGENDA_CLOSED);
                verifyNoInteractions(agendaOptionRepository, voteRepository);
            }

            @Test
            @DisplayName("없거나 다른 안건의 선지면 OPTION_NOT_FOUND를 던진다")
            void rejectsOptionOfOtherAgenda() {
                AgendaItem budget = openBudget();
                givenLockedRoomAndMember(me);
                givenAgenda(budget);
                given(agendaOptionRepository.findByIdAndAgendaItemId(99L, budget.getId()))
                        .willReturn(Optional.empty());

                assertError(
                        () ->
                                agendaService.deleteBudgetOption(
                                        ROOM_ID, budget.getId(), 99L, USER_ID),
                        AgendaErrorCode.OPTION_NOT_FOUND);
                verifyNothingDeleted();
            }

            @Test
            @DisplayName("다른 멤버가 올린 선지면 NOT_OPTION_OWNER를 던지고 지우지 않는다")
            void rejectsOptionOfOtherMember() {
                AgendaItem budget = openBudget();
                AgendaOption jieuns = budget(budget, 1_200_000L, 1, jieun);
                givenLockedRoomAndMember(me);
                givenAgenda(budget);
                givenOption(budget, jieuns);

                assertError(() -> delete(budget, jieuns), AgendaErrorCode.NOT_OPTION_OWNER);
                verifyNothingDeleted();
            }

            @Test
            @DisplayName("설문 응답에서 나온 선지면 올린 사람이 없어 NOT_OPTION_OWNER를 던진다")
            void rejectsSurveyOption() {
                AgendaItem budget = openBudget();
                AgendaOption fromSurvey = budget(budget, 600_000L, 0, null);
                givenLockedRoomAndMember(me);
                givenAgenda(budget);
                givenOption(budget, fromSurvey);

                assertError(() -> delete(budget, fromSurvey), AgendaErrorCode.NOT_OPTION_OWNER);
                verifyNothingDeleted();
            }

            @Test
            @DisplayName("다른 멤버의 표가 있으면 OPTION_HAS_VOTES를 던지고 data에 그 표 수를 담는다")
            void rejectsOptionWithOthersVotes() {
                AgendaItem budget = openBudget();
                AgendaOption mine = budget(budget, 1_200_000L, 1, me);
                givenLockedRoomAndMember(me);
                givenAgenda(budget);
                givenOption(budget, mine);
                givenOthersVoteCount(mine, 2);

                assertThatThrownBy(() -> delete(budget, mine))
                        .isInstanceOf(BusinessException.class)
                        .satisfies(e -> {
                            BusinessException be = (BusinessException) e;
                            assertThat(be.getErrorCode())
                                    .isEqualTo(AgendaErrorCode.OPTION_HAS_VOTES);
                            assertThat(be.getData()).isEqualTo(Map.of("voteCount", 2L));
                        });
                verifyNothingDeleted();
            }
        }
    }
}
