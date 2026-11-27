package world.chakchak.domain.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
import org.mockito.ArgumentCaptor;
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
import world.chakchak.domain.agenda.service.AgendaChangePublisher;
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
import world.chakchak.domain.survey.entity.ActivityCode;
import world.chakchak.domain.survey.entity.Pace;
import world.chakchak.domain.survey.entity.StayType;
import world.chakchak.domain.survey.entity.SurveyResponse;
import world.chakchak.domain.survey.service.SurveyResponseService;
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
    @Mock private SurveyResponseService surveyResponseService;
    @Mock private AgendaChangePublisher agendaChangePublisher;

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
        class Failure {

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
        class Failure {

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

        // 13-2 마감 처리의 앞부분: 방 잠금, 잠근 뒤의 시각, 투표 중 안건
        private void givenOverdueCheck(LocalDateTime now, AgendaItem... voting) {
            given(roomService.getForUpdate(ROOM_ID)).willReturn(room);
            given(clock.instant()).willReturn(now.atZone(SEOUL).toInstant());
            given(clock.getZone()).willReturn(SEOUL);
            given(agendaItemRepository.findAllByRoomIdAndStatus(ROOM_ID, AgendaStatus.VOTING))
                    .willReturn(List.of(voting));
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

                boolean resolved = agendaService.judge(room, me, budget, NOW);

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

                agendaService.judge(room, me, budget, NOW);

                assertThat(room.getStatus()).isEqualTo(RoomStatus.PLANNING);
            }

            @Test
            @DisplayName("최종 확정에 자격자 전원이 동의하면 방이 FINALIZED가 되고 확정 시각이 기록된다")
            void finalizesRoomWhenAllAgreed() {
                reviewing();
                AgendaItem finalPlan = openAgenda(AgendaSubject.FINAL_PLAN, 2);
                AgendaOption agree = finalOption(finalPlan, FinalPlanChoice.AGREE);
                givenVotes(finalPlan, vote(finalPlan, agree, me), vote(finalPlan, agree, jieun));

                boolean resolved = agendaService.judge(room, me, finalPlan, NOW);

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

                boolean resolved = agendaService.judge(room, me, finalPlan, NOW);

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
                boolean resolved = agendaService.judge(room, null, budget, NOW);

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
                boolean resolved = agendaService.judge(room, null, finalPlan, NOW);

                assertThat(resolved).isTrue();
                assertThat(room.getStatus()).isEqualTo(RoomStatus.FINALIZED);
            }

            @Test
            @DisplayName("마지막 활동 멤버가 나가면 동의 표가 만장일치가 되어도 안건과 방 상태를 유지한다")
            void keepsFinalPlanVotingWhenLastMemberLeaves() {
                reviewing();
                AgendaItem finalPlan = openAgenda(AgendaSubject.FINAL_PLAN, 2);
                AgendaOption agree = finalOption(finalPlan, FinalPlanChoice.AGREE);
                given(roomMemberService.countActiveMembers(ROOM_ID)).willReturn(0L);
                given(agendaItemRepository.findAllByRoomIdAndStatus(ROOM_ID, AgendaStatus.VOTING))
                        .willReturn(List.of(finalPlan));

                agendaService.memberLeft(room, jieun, NOW);

                assertThat(finalPlan.getStatus()).isEqualTo(AgendaStatus.VOTING);
                assertThat(finalPlan.getResolvedOptionId()).isNull();
                assertThat(room.getStatus()).isEqualTo(RoomStatus.REVIEWING);
                assertThat(room.getFinalizedAt()).isNull();
            }

            @Test
            @DisplayName("표를 낸 멤버가 나가면 표가 지워지고 투표자 수가 줄어 남은 표로 안건이 결정된다")
            void deletesVoteAndResolvesWhenVoterLeaves() {
                RoomMember leaving = member("현우", NOW.minusDays(1));
                AgendaItem budget = openAgenda(AgendaSubject.BUDGET, 3);
                AgendaOption low = budget(budget, 600_000L, 0, null);
                given(roomMemberService.countActiveMembers(ROOM_ID)).willReturn(2L);
                given(agendaItemRepository.findAllByRoomIdAndStatus(ROOM_ID, AgendaStatus.VOTING))
                        .willReturn(List.of(budget));
                // 나간 멤버의 표를 지운 뒤의 조회 결과다
                givenVotes(budget, vote(budget, low, me), vote(budget, low, jieun));
                givenLatestRounds(budget, openAgenda(AgendaSubject.STAY, 2));

                agendaService.memberLeft(room, leaving, NOW);

                verify(voteRepository)
                        .deleteByAgendaItemIdAndMemberId(budget.getId(), leaving.getId());
                assertThat(budget.getTotalVoter()).isEqualTo(2);
                assertThat(budget.getStatus()).isEqualTo(AgendaStatus.RESOLVED);
                assertThat(budget.getResolvedOptionId()).isEqualTo(low.getId());
            }

            @Test
            @DisplayName("안건이 열린 뒤 합류해 투표 자격이 없는 멤버가 나가면 표를 지우지 않고 투표자 수도 그대로다")
            void keepsVotersWhenIneligibleMemberLeaves() {
                RoomMember lateJoiner = member("서연", NOW);
                AgendaItem budget = openAgenda(AgendaSubject.BUDGET, 2);
                given(roomMemberService.countActiveMembers(ROOM_ID)).willReturn(2L);
                given(agendaItemRepository.findAllByRoomIdAndStatus(ROOM_ID, AgendaStatus.VOTING))
                        .willReturn(List.of(budget));

                agendaService.memberLeft(room, lateJoiner, NOW);

                verify(voteRepository, never())
                        .deleteByAgendaItemIdAndMemberId(budget.getId(), lateJoiner.getId());
                assertThat(budget.getTotalVoter()).isEqualTo(2);
                assertThat(budget.getStatus()).isEqualTo(AgendaStatus.VOTING);
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

            @Test
            @DisplayName("기한이 지난 설문 안건은 미결로 끝나고 방은 SURVEYING에 머문다")
            void expiresOverdueSurveyAgenda() {
                AgendaItem budget = openAgenda(AgendaSubject.BUDGET, 2);
                givenOverdueCheck(budget.getDeadlineAt(), budget);

                agendaService.expireOverdueAgendas(ROOM_ID);

                assertThat(budget.getStatus()).isEqualTo(AgendaStatus.EXPIRED);
                assertThat(room.getStatus()).isEqualTo(RoomStatus.SURVEYING);
            }

            @Test
            @DisplayName("기한이 지난 최종 확정 안건은 미결로 끝나고 방이 PLANNING으로 돌아간다")
            void expiresOverdueFinalPlan() {
                reviewing();
                AgendaItem finalPlan = openAgenda(AgendaSubject.FINAL_PLAN, 2);
                givenOverdueCheck(finalPlan.getDeadlineAt().plusMinutes(1), finalPlan);

                agendaService.expireOverdueAgendas(ROOM_ID);

                assertThat(finalPlan.getStatus()).isEqualTo(AgendaStatus.EXPIRED);
                assertThat(room.getStatus()).isEqualTo(RoomStatus.PLANNING);
            }

            @Test
            @DisplayName("같은 방에서 기한이 지난 안건만 미결로 끝내고 아직 기한이 남은 안건은 그대로 둔다")
            void expiresOnlyOverdueAgendas() {
                AgendaItem overdue = openAgenda(AgendaSubject.BUDGET, 2);
                AgendaItem open =
                        withId(
                                AgendaItem.create(
                                        room,
                                        AgendaSubject.STAY,
                                        1,
                                        "안건 제목",
                                        2,
                                        NOW.minusHours(1),
                                        NOW.plusDays(5)));
                givenOverdueCheck(overdue.getDeadlineAt(), overdue, open);

                agendaService.expireOverdueAgendas(ROOM_ID);

                assertThat(overdue.getStatus()).isEqualTo(AgendaStatus.EXPIRED);
                assertThat(open.getStatus()).isEqualTo(AgendaStatus.VOTING);
            }

            @Test
            @DisplayName("출발일 00:00이 된 최종 확정 안건은 최종 집계를 위해 투표 중으로 남고 방도 그대로다")
            void keepsFinalPlanForFinalTally() {
                reviewing();
                AgendaItem finalPlan = openAgenda(AgendaSubject.FINAL_PLAN, 2);
                givenOverdueCheck(room.getStartDate().atStartOfDay(), finalPlan);

                agendaService.expireOverdueAgendas(ROOM_ID);

                assertThat(finalPlan.getStatus()).isEqualTo(AgendaStatus.VOTING);
                assertThat(room.getStatus()).isEqualTo(RoomStatus.REVIEWING);
            }

            @Test
            @DisplayName("기한 지난 안건이 있는 방을 찾을 때 지금 시각과 오늘 날짜로 조회한다")
            void findsOverdueRoomsWithCurrentTime() {
                given(clock.instant()).willReturn(NOW.atZone(SEOUL).toInstant());
                given(clock.getZone()).willReturn(SEOUL);
                given(
                                agendaItemRepository.findRoomIdsWithOverdueAgendas(
                                        AgendaStatus.VOTING, NOW, NOW.toLocalDate()))
                        .willReturn(List.of(ROOM_ID));

                assertThat(agendaService.findRoomIdsWithOverdueAgendas()).containsExactly(ROOM_ID);
            }

            @Test
            @DisplayName("결정되지 않은 투표는 방 활동 기록을 남기지 않고 표 수만 vote.updated로 알린다")
            void publishesOnlyVoteUpdatedWhenUndecided() {
                AgendaItem budget = openAgenda(AgendaSubject.BUDGET, 2);
                AgendaOption low = budget(budget, 600_000L, 0, null);
                givenLockedRoomAndMember(me);
                givenAgenda(budget);
                givenOption(budget, low);
                givenMyVote(budget, null);
                givenVotes(budget, vote(budget, low, me));

                AgendaDetailResponse response = voteFor(budget, low, null);

                verify(agendaChangePublisher).voteUpdated(ROOM_ID, response);
                verifyNoMoreInteractions(agendaChangePublisher);
            }

            @Test
            @DisplayName("투표로 안건이 결정되면 투표한 나를 행위자로 결정 기록을 남긴 뒤 표 수를 알린다")
            void recordsResolvedByVoter() {
                AgendaItem budget = openAgenda(AgendaSubject.BUDGET, 2);
                AgendaOption low = budget(budget, 600_000L, 0, null);
                givenLockedRoomAndMember(me);
                givenAgenda(budget);
                givenOption(budget, low);
                givenMyVote(budget, null);
                givenVotes(budget, vote(budget, low, jieun), vote(budget, low, me));
                givenLatestRounds(budget, openAgenda(AgendaSubject.STAY, 2));

                AgendaDetailResponse response = voteFor(budget, low, null);

                InOrder order = inOrder(agendaChangePublisher);
                order.verify(agendaChangePublisher).resolved(me, budget, low);
                order.verify(agendaChangePublisher).voteUpdated(ROOM_ID, response);
                verifyNoMoreInteractions(agendaChangePublisher);
            }

            @Test
            @DisplayName("마지막 설문 항목이 결정되면 결정 기록 다음에 조율 완료 기록을 같은 행위자로 남긴다")
            void recordsCoordinationCompletedAfterResolved() {
                AgendaItem budget = openAgenda(AgendaSubject.BUDGET, 2);
                AgendaOption low = budget(budget, 600_000L, 0, null);
                givenVotes(budget, vote(budget, low, me), vote(budget, low, jieun));
                givenLatestRounds(
                        budget,
                        resolvedAgenda(AgendaSubject.STAY),
                        resolvedAgenda(AgendaSubject.PACE));

                agendaService.judge(room, me, budget, NOW);

                InOrder order = inOrder(agendaChangePublisher);
                order.verify(agendaChangePublisher).resolved(me, budget, low);
                order.verify(agendaChangePublisher).coordinationCompleted(room, me);
                verifyNoMoreInteractions(agendaChangePublisher);
            }

            @Test
            @DisplayName("최종 확정이 동의로 결정되면 계획 확정 한 줄만 남기고 안건 결정 기록은 따로 남기지 않는다")
            void recordsOnlyPlanFinalizedOnAgree() {
                reviewing();
                AgendaItem finalPlan = openAgenda(AgendaSubject.FINAL_PLAN, 2);
                AgendaOption agree = finalOption(finalPlan, FinalPlanChoice.AGREE);
                givenVotes(finalPlan, vote(finalPlan, agree, me), vote(finalPlan, agree, jieun));

                agendaService.judge(room, me, finalPlan, NOW);

                verify(agendaChangePublisher).planFinalized(room, me);
                verifyNoMoreInteractions(agendaChangePublisher);
            }

            @Test
            @DisplayName("최종 확정이 반대로 결정되면 안건 결정 기록만 남긴다")
            void recordsResolvedOnReject() {
                reviewing();
                AgendaItem finalPlan = openAgenda(AgendaSubject.FINAL_PLAN, 3);
                AgendaOption reject = finalOption(finalPlan, FinalPlanChoice.REJECT);
                givenVotes(finalPlan, vote(finalPlan, reject, me));

                agendaService.judge(room, me, finalPlan, NOW);

                verify(agendaChangePublisher).resolved(me, finalPlan, reject);
                verifyNoMoreInteractions(agendaChangePublisher);
            }

            @Test
            @DisplayName("멤버가 나가 남은 표로 안건이 결정되면 나간 멤버가 아니라 시스템을 행위자로 결정 기록을 남긴다")
            void recordsResolvedBySystemWhenMemberLeaves() {
                RoomMember leaving = member("현우", NOW.minusDays(1));
                AgendaItem budget = openAgenda(AgendaSubject.BUDGET, 3);
                AgendaOption low = budget(budget, 600_000L, 0, null);
                given(roomMemberService.countActiveMembers(ROOM_ID)).willReturn(2L);
                given(agendaItemRepository.findAllByRoomIdAndStatus(ROOM_ID, AgendaStatus.VOTING))
                        .willReturn(List.of(budget));
                givenVotes(budget, vote(budget, low, me), vote(budget, low, jieun));
                givenLatestRounds(budget, openAgenda(AgendaSubject.STAY, 2));

                agendaService.memberLeft(room, leaving, NOW);

                verify(agendaChangePublisher).resolved(null, budget, low);
                verifyNoMoreInteractions(agendaChangePublisher);
            }

            @Test
            @DisplayName("멤버가 나가 자격자가 0명이 된 안건은 남은 멤버가 있으면 시스템을 행위자로 미결 기록을 남긴다")
            void recordsExpiredWhenLastVoterLeaves() {
                RoomMember leaving = member("현우", NOW.minusDays(1));
                AgendaItem pace = openAgenda(AgendaSubject.PACE, 1);
                given(roomMemberService.countActiveMembers(ROOM_ID)).willReturn(1L);
                given(agendaItemRepository.findAllByRoomIdAndStatus(ROOM_ID, AgendaStatus.VOTING))
                        .willReturn(List.of(pace));

                agendaService.memberLeft(room, leaving, NOW);

                assertThat(pace.getStatus()).isEqualTo(AgendaStatus.EXPIRED);
                verify(agendaChangePublisher).expired(null, pace);
                verifyNoMoreInteractions(agendaChangePublisher);
            }

            @Test
            @DisplayName("기한이 지난 안건을 미결로 끝내면 시스템을 행위자로 미결 기록을 남기고 기한이 남은 안건은 남기지 않는다")
            void recordsExpiredForOverdueAgendasOnly() {
                AgendaItem overdue = openAgenda(AgendaSubject.BUDGET, 2);
                AgendaItem open =
                        withId(
                                AgendaItem.create(
                                        room,
                                        AgendaSubject.STAY,
                                        1,
                                        "안건 제목",
                                        2,
                                        NOW.minusHours(1),
                                        NOW.plusDays(5)));
                givenOverdueCheck(overdue.getDeadlineAt(), overdue, open);

                agendaService.expireOverdueAgendas(ROOM_ID);

                verify(agendaChangePublisher).expired(null, overdue);
                verifyNoMoreInteractions(agendaChangePublisher);
            }
        }

        @Nested
        class Failure {

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
            @DisplayName("방이 SURVEYING·PLANNING·REVIEWING이 아니면 INVALID_STATUS를 던지고 안건을 조회하지 않는다")
            void rejectsInvalidRoomStatus() {
                ReflectionTestUtils.setField(room, "status", RoomStatus.FINALIZED);
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

                boolean resolved = agendaService.judge(room, me, budget, NOW);

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
    class DeadlineJobs {

        // 출발일 2027-07-01 00:00
        private static final LocalDateTime D_DAY = LocalDateTime.of(2027, 7, 1, 0, 0);

        @BeforeEach
        void giveRoomId() {
            ReflectionTestUtils.setField(room, "id", ROOM_ID);
        }

        private void givenLockedRoomAt(LocalDateTime now) {
            given(roomService.getForUpdate(ROOM_ID)).willReturn(room);
            given(clock.instant()).willReturn(now.atZone(SEOUL).toInstant());
            given(clock.getZone()).willReturn(SEOUL);
        }

        private void surveyClosed() {
            ReflectionTestUtils.setField(room, "surveyClosedAt", NOW.minusDays(1));
        }

        private AgendaItem openItem(AgendaSubject subject) {
            return withId(
                    AgendaItem.create(
                            room, subject, 1, "안건 제목", 3, NOW.minusHours(1), NOW.plusDays(3)));
        }

        private AgendaItem resolvedPace() {
            AgendaItem pace = openItem(AgendaSubject.PACE);
            pace.resolve(withId(AgendaOption.pace(pace, Pace.RELAXED, 0)), NOW);
            return pace;
        }

        private AgendaOption stayOption(AgendaItem agenda, StayType type, int index) {
            return withId(AgendaOption.stay(agenda, type, index));
        }

        private void givenLatest(AgendaItem... agendas) {
            for (AgendaItem agenda : agendas) {
                given(
                                agendaItemRepository.findTopByRoomIdAndSubjectOrderByRoundDesc(
                                        ROOM_ID, agenda.getSubject()))
                        .willReturn(Optional.of(agenda));
            }
        }

        private void givenVotes(AgendaItem agenda, Vote... votes) {
            given(voteRepository.findAllByAgendaItemId(agenda.getId())).willReturn(List.of(votes));
        }

        @Nested
        class Success {

            @Test
            @DisplayName("표가 갈린 투표 중 항목과 미결 항목을 최다 득표로 결정하고 세 항목이 끝나면 방이 PLANNING이 된다")
            void talliesSurveyAgendasAndStartsPlanning() {
                surveyClosed();
                givenLockedRoomAt(D_DAY);
                AgendaItem budget = openItem(AgendaSubject.BUDGET);
                AgendaOption low = budget(budget, 600_000L, 0, null);
                AgendaOption high = budget(budget, 1_000_000L, 1, null);
                RoomMember hyunwoo = member("현우", NOW.minusDays(1));
                givenVotes(
                        budget,
                        vote(budget, high, me),
                        vote(budget, high, jieun),
                        vote(budget, low, hyunwoo));
                AgendaItem stay = openItem(AgendaSubject.STAY);
                AgendaOption hotel = stayOption(stay, StayType.HOTEL, 0);
                givenVotes(stay, vote(stay, hotel, me));
                stay.expire();
                AgendaItem pace = resolvedPace();
                givenLatest(budget, stay, pace);

                agendaService.runFinalTally(ROOM_ID);

                assertThat(budget.getStatus()).isEqualTo(AgendaStatus.RESOLVED);
                assertThat(budget.getResolvedOptionId()).isEqualTo(high.getId());
                assertThat(stay.getStatus()).isEqualTo(AgendaStatus.RESOLVED);
                assertThat(stay.getResolvedOptionId()).isEqualTo(hotel.getId());
                assertThat(room.getStatus()).isEqualTo(RoomStatus.PLANNING);
            }

            @Test
            @DisplayName("미결 회차에 남은 나간 멤버의 표는 집계에서 뺀다")
            void excludesVotesOfLeftMembers() {
                surveyClosed();
                givenLockedRoomAt(D_DAY);
                AgendaItem stay = openItem(AgendaSubject.STAY);
                AgendaOption hotel = stayOption(stay, StayType.HOTEL, 0);
                AgendaOption personal = stayOption(stay, StayType.PERSONAL, 1);
                RoomMember leftA = member("나간A", NOW.minusDays(1));
                RoomMember leftB = member("나간B", NOW.minusDays(1));
                Vote leftVoteA = vote(stay, hotel, leftA);
                Vote leftVoteB = vote(stay, hotel, leftB);
                Vote myVote = vote(stay, personal, me);
                leftA.leave(NOW);
                leftB.leave(NOW);
                stay.expire();
                givenVotes(stay, leftVoteA, leftVoteB, myVote);
                AgendaItem budget = openItem(AgendaSubject.BUDGET);
                givenVotes(budget, vote(budget, budget(budget, 600_000L, 0, null), me));
                givenLatest(budget, stay, resolvedPace());

                agendaService.runFinalTally(ROOM_ID);

                assertThat(stay.getResolvedOptionId()).isEqualTo(personal.getId());
                assertThat(room.getStatus()).isEqualTo(RoomStatus.PLANNING);
            }

            @Test
            @DisplayName("활동 멤버의 표가 0개인 항목이 있으면 그 항목은 미결, 다른 항목은 결정되고 방은 EXPIRED가 된다")
            void expiresRoomWhenAnItemHasNoVotes() {
                surveyClosed();
                givenLockedRoomAt(D_DAY);
                AgendaItem budget = openItem(AgendaSubject.BUDGET);
                AgendaOption low = budget(budget, 600_000L, 0, null);
                givenVotes(budget, vote(budget, low, me));
                AgendaItem stay = openItem(AgendaSubject.STAY);
                givenVotes(stay);
                givenLatest(budget, stay, resolvedPace());

                agendaService.runFinalTally(ROOM_ID);

                assertThat(budget.getStatus()).isEqualTo(AgendaStatus.RESOLVED);
                assertThat(stay.getStatus()).isEqualTo(AgendaStatus.EXPIRED);
                assertThat(room.getStatus()).isEqualTo(RoomStatus.EXPIRED);
            }

            @Test
            @DisplayName("1위가 동점인 항목이 있으면 그 항목은 미결, 다른 항목은 결정되고 방은 EXPIRED가 된다")
            void expiresRoomWhenAnItemIsTied() {
                surveyClosed();
                givenLockedRoomAt(D_DAY);
                AgendaItem budget = openItem(AgendaSubject.BUDGET);
                AgendaOption low = budget(budget, 600_000L, 0, null);
                AgendaOption high = budget(budget, 1_000_000L, 1, null);
                givenVotes(budget, vote(budget, low, me), vote(budget, high, jieun));
                AgendaItem stay = openItem(AgendaSubject.STAY);
                AgendaOption hotel = stayOption(stay, StayType.HOTEL, 0);
                givenVotes(stay, vote(stay, hotel, me));
                givenLatest(budget, stay, resolvedPace());

                agendaService.runFinalTally(ROOM_ID);

                assertThat(budget.getStatus()).isEqualTo(AgendaStatus.EXPIRED);
                assertThat(budget.getResolvedOptionId()).isNull();
                assertThat(stay.getStatus()).isEqualTo(AgendaStatus.RESOLVED);
                assertThat(stay.getResolvedOptionId()).isEqualTo(hotel.getId());
                assertThat(room.getStatus()).isEqualTo(RoomStatus.EXPIRED);
            }

            @Test
            @DisplayName("D-DAY까지 설문이 마감되지 않은 방은 EXPIRED가 된다")
            void expiresRoomWithOpenSurvey() {
                givenLockedRoomAt(D_DAY);

                agendaService.runFinalTally(ROOM_ID);

                assertThat(room.getStatus()).isEqualTo(RoomStatus.EXPIRED);
                verifyNoInteractions(voteRepository);
            }

            @Test
            @DisplayName("설문은 마감됐지만 D-DAY가 지나 안건이 열리지 못한 방은 EXPIRED가 된다")
            void expiresRoomWithoutAgendas() {
                surveyClosed();
                givenLockedRoomAt(D_DAY);

                agendaService.runFinalTally(ROOM_ID);

                assertThat(room.getStatus()).isEqualTo(RoomStatus.EXPIRED);
            }

            @Test
            @DisplayName("최종 투표는 미투표자가 있어도 낸 표가 모두 동의면 방이 FINALIZED가 된다")
            void finalizesWhenCastVotesAgree() {
                ReflectionTestUtils.setField(room, "status", RoomStatus.REVIEWING);
                givenLockedRoomAt(D_DAY);
                AgendaItem finalPlan = openItem(AgendaSubject.FINAL_PLAN);
                AgendaOption agree =
                        withId(AgendaOption.finalPlan(finalPlan, FinalPlanChoice.AGREE));
                givenVotes(finalPlan, vote(finalPlan, agree, me), vote(finalPlan, agree, jieun));
                givenLatest(finalPlan);

                agendaService.runFinalTally(ROOM_ID);

                assertThat(finalPlan.getStatus()).isEqualTo(AgendaStatus.RESOLVED);
                assertThat(finalPlan.getResolvedOptionId()).isEqualTo(agree.getId());
                assertThat(room.getStatus()).isEqualTo(RoomStatus.FINALIZED);
                assertThat(room.getFinalizedAt()).isEqualTo(D_DAY);
            }

            @Test
            @DisplayName("최종 투표에 동의 표가 더 많아도 반대 표가 섞여 있으면 반대로 정하고 방이 PLANNING으로 돌아간다")
            void returnsToPlanningWhenAnyRejectVote() {
                ReflectionTestUtils.setField(room, "status", RoomStatus.REVIEWING);
                givenLockedRoomAt(D_DAY);
                AgendaItem finalPlan = openItem(AgendaSubject.FINAL_PLAN);
                AgendaOption agree =
                        withId(AgendaOption.finalPlan(finalPlan, FinalPlanChoice.AGREE));
                AgendaOption reject =
                        withId(AgendaOption.finalPlan(finalPlan, FinalPlanChoice.REJECT));
                RoomMember hyunwoo = member("현우", NOW.minusDays(1));
                givenVotes(
                        finalPlan,
                        vote(finalPlan, agree, me),
                        vote(finalPlan, agree, jieun),
                        vote(finalPlan, reject, hyunwoo));
                givenLatest(finalPlan);

                agendaService.runFinalTally(ROOM_ID);

                assertThat(finalPlan.getStatus()).isEqualTo(AgendaStatus.RESOLVED);
                assertThat(finalPlan.getResolvedOptionId()).isEqualTo(reject.getId());
                assertThat(room.getStatus()).isEqualTo(RoomStatus.PLANNING);
                assertThat(room.getFinalizedAt()).isNull();
            }

            @Test
            @DisplayName("최종 투표에 표가 없으면 안건은 미결로 끝나고 방이 PLANNING으로 돌아간다")
            void returnsToPlanningWithoutFinalVotes() {
                ReflectionTestUtils.setField(room, "status", RoomStatus.REVIEWING);
                givenLockedRoomAt(D_DAY);
                AgendaItem finalPlan = openItem(AgendaSubject.FINAL_PLAN);
                givenVotes(finalPlan);
                givenLatest(finalPlan);

                agendaService.runFinalTally(ROOM_ID);

                assertThat(finalPlan.getStatus()).isEqualTo(AgendaStatus.EXPIRED);
                assertThat(room.getStatus()).isEqualTo(RoomStatus.PLANNING);
            }

            @Test
            @DisplayName("마지막 멤버가 나가 최종 안건이 이미 미결이고 방만 REVIEWING이면 방이 PLANNING으로 돌아간다")
            void returnsToPlanningWhenFinalPlanAlreadyExpired() {
                ReflectionTestUtils.setField(room, "status", RoomStatus.REVIEWING);
                givenLockedRoomAt(D_DAY);
                AgendaItem finalPlan = openItem(AgendaSubject.FINAL_PLAN);
                finalPlan.expire();
                givenLatest(finalPlan);

                agendaService.runFinalTally(ROOM_ID);

                assertThat(finalPlan.getStatus()).isEqualTo(AgendaStatus.EXPIRED);
                assertThat(room.getStatus()).isEqualTo(RoomStatus.PLANNING);
                verifyNoInteractions(voteRepository);
            }

            @Test
            @DisplayName("이미 PLANNING이 된 방은 다시 집계해도 바뀌지 않는다")
            void doesNothingForPlanningRoom() {
                ReflectionTestUtils.setField(room, "status", RoomStatus.PLANNING);
                givenLockedRoomAt(D_DAY);

                agendaService.runFinalTally(ROOM_ID);

                assertThat(room.getStatus()).isEqualTo(RoomStatus.PLANNING);
                verifyNoInteractions(agendaItemRepository, voteRepository);
            }

            @Test
            @DisplayName("D-DAY 집계로 설문 항목이 결정되면 시스템을 행위자로 항목마다 결정 기록을 남기고 세 항목이 끝나면 조율 완료를 남긴다")
            void recordsFinalTallyResolutions() {
                surveyClosed();
                givenLockedRoomAt(D_DAY);
                AgendaItem budget = openItem(AgendaSubject.BUDGET);
                AgendaOption high = budget(budget, 1_000_000L, 0, null);
                givenVotes(budget, vote(budget, high, me));
                AgendaItem stay = openItem(AgendaSubject.STAY);
                AgendaOption hotel = stayOption(stay, StayType.HOTEL, 0);
                givenVotes(stay, vote(stay, hotel, me));
                stay.expire();
                // 이미 결정된 항목은 그때 기록을 남겼으므로 다시 남기지 않는다
                givenLatest(budget, stay, resolvedPace());

                agendaService.runFinalTally(ROOM_ID);

                InOrder order = inOrder(agendaChangePublisher);
                order.verify(agendaChangePublisher).resolved(null, budget, high);
                order.verify(agendaChangePublisher).resolved(null, stay, hotel);
                order.verify(agendaChangePublisher).coordinationCompleted(room, null);
                verifyNoMoreInteractions(agendaChangePublisher);
            }

            @Test
            @DisplayName("D-DAY 집계에서 정하지 못한 투표 중 항목은 미결 기록을 남기고, 방이 만료되면 조율 완료 대신 방 만료를 알린다")
            void recordsExpiredItemWhenRoomExpires() {
                surveyClosed();
                givenLockedRoomAt(D_DAY);
                AgendaItem budget = openItem(AgendaSubject.BUDGET);
                AgendaOption low = budget(budget, 600_000L, 0, null);
                givenVotes(budget, vote(budget, low, me));
                AgendaItem stay = openItem(AgendaSubject.STAY);
                givenVotes(stay);
                givenLatest(budget, stay, resolvedPace());

                agendaService.runFinalTally(ROOM_ID);

                verify(agendaChangePublisher).resolved(null, budget, low);
                verify(agendaChangePublisher).expired(null, stay);
                verify(agendaChangePublisher).roomExpired(room);
                verifyNoMoreInteractions(agendaChangePublisher);
            }

            @Test
            @DisplayName("D-DAY 집계에서 이미 미결이던 회차를 정하지 못하면 미결 기록을 다시 남기지 않고 방 만료만 알린다")
            void skipsRecordForAlreadyExpiredItem() {
                surveyClosed();
                givenLockedRoomAt(D_DAY);
                AgendaItem budget = openItem(AgendaSubject.BUDGET);
                AgendaOption low = budget(budget, 600_000L, 0, null);
                givenVotes(budget, vote(budget, low, me));
                AgendaItem stay = openItem(AgendaSubject.STAY);
                givenVotes(stay);
                stay.expire();
                givenLatest(budget, stay, resolvedPace());

                agendaService.runFinalTally(ROOM_ID);

                verify(agendaChangePublisher).resolved(null, budget, low);
                verify(agendaChangePublisher).roomExpired(room);
                verifyNoMoreInteractions(agendaChangePublisher);
            }

            @Test
            @DisplayName("D-DAY 최종 투표가 동의로 정해지면 시스템을 행위자로 계획 확정 한 줄만 남긴다")
            void recordsPlanFinalizedByFinalTally() {
                ReflectionTestUtils.setField(room, "status", RoomStatus.REVIEWING);
                givenLockedRoomAt(D_DAY);
                AgendaItem finalPlan = openItem(AgendaSubject.FINAL_PLAN);
                AgendaOption agree =
                        withId(AgendaOption.finalPlan(finalPlan, FinalPlanChoice.AGREE));
                givenVotes(finalPlan, vote(finalPlan, agree, me));
                givenLatest(finalPlan);

                agendaService.runFinalTally(ROOM_ID);

                verify(agendaChangePublisher).planFinalized(room, null);
                verifyNoMoreInteractions(agendaChangePublisher);
            }

            @Test
            @DisplayName("D-DAY 최종 투표에 표가 없으면 시스템을 행위자로 미결 기록을 남긴다")
            void recordsFinalPlanExpiredByFinalTally() {
                ReflectionTestUtils.setField(room, "status", RoomStatus.REVIEWING);
                givenLockedRoomAt(D_DAY);
                AgendaItem finalPlan = openItem(AgendaSubject.FINAL_PLAN);
                givenVotes(finalPlan);
                givenLatest(finalPlan);

                agendaService.runFinalTally(ROOM_ID);

                verify(agendaChangePublisher).expired(null, finalPlan);
                verifyNoMoreInteractions(agendaChangePublisher);
            }

            @Test
            @DisplayName("마지막 멤버가 나가 이미 미결인 최종 안건은 D-DAY 집계에서 기록을 다시 남기지 않는다")
            void skipsRecordForAlreadyExpiredFinalPlan() {
                ReflectionTestUtils.setField(room, "status", RoomStatus.REVIEWING);
                givenLockedRoomAt(D_DAY);
                AgendaItem finalPlan = openItem(AgendaSubject.FINAL_PLAN);
                finalPlan.expire();
                givenLatest(finalPlan);

                agendaService.runFinalTally(ROOM_ID);

                verifyNoInteractions(agendaChangePublisher);
            }
        }

        @Nested
        class Failure {

            @Test
            @DisplayName("D-DAY 전이면 최종 집계를 하지 않는다")
            void skipsTallyBeforeDepartureDay() {
                surveyClosed();
                givenLockedRoomAt(D_DAY.minusSeconds(1));

                agendaService.runFinalTally(ROOM_ID);

                assertThat(room.getStatus()).isEqualTo(RoomStatus.SURVEYING);
                verifyNoInteractions(agendaItemRepository, voteRepository);
            }

            @Test
            @DisplayName("최종 투표 중인 방에 최종 확정 안건이 없으면 코드 버그로 보고 예외를 던진다")
            void rejectsReviewingRoomWithoutFinalPlan() {
                ReflectionTestUtils.setField(room, "status", RoomStatus.REVIEWING);
                givenLockedRoomAt(D_DAY);

                assertThatThrownBy(() -> agendaService.runFinalTally(ROOM_ID))
                        .isInstanceOf(IllegalStateException.class);
                assertThat(room.getStatus()).isEqualTo(RoomStatus.REVIEWING);
            }
        }
    }

    @Nested
    class OpenSurveyAgendas {

        // 서비스가 저장한 안건과 선지
        private final List<AgendaItem> savedAgendas = new ArrayList<>();
        private final List<AgendaOption> savedOptions = new ArrayList<>();

        @BeforeEach
        void closedSurvey() {
            ReflectionTestUtils.setField(room, "id", ROOM_ID);
            ReflectionTestUtils.setField(room, "surveyClosedAt", NOW);
        }

        private SurveyResponse answer(
                RoomMember member, long budget, StayType stay, Pace pace, LocalDateTime at) {
            return SurveyResponse.createForSurveying(
                    member, budget, stay, pace, List.of(ActivityCode.FOOD), "없음", clockAt(at));
        }

        private void givenNoSurveyAgendas() {
            given(
                            agendaItemRepository.findTopByRoomIdAndSubjectOrderByRoundDesc(
                                    ROOM_ID, AgendaSubject.BUDGET))
                    .willReturn(Optional.empty());
        }

        // 저장하면 DB처럼 id를 채운다
        private void givenAnswers(SurveyResponse... answers) {
            given(surveyResponseService.getActiveMemberResponses(ROOM_ID))
                    .willReturn(List.of(answers));
            given(roomMemberService.countActiveMembers(ROOM_ID)).willReturn(2L);
            given(agendaItemRepository.save(any(AgendaItem.class)))
                    .willAnswer(invocation -> {
                        AgendaItem agenda = withId(invocation.<AgendaItem>getArgument(0));
                        savedAgendas.add(agenda);
                        return agenda;
                    });
            given(agendaOptionRepository.saveAll(any()))
                    .willAnswer(invocation -> {
                        List<AgendaOption> options = invocation.getArgument(0);
                        options.forEach(option -> savedOptions.add(withId(option)));
                        return options;
                    });
        }

        private AgendaItem saved(AgendaSubject subject) {
            return savedAgendas.stream()
                    .filter(agenda -> agenda.getSubject() == subject)
                    .findFirst()
                    .orElseThrow();
        }

        private List<String> optionValues(AgendaItem agenda) {
            return savedOptions.stream()
                    .filter(option -> option.getAgendaItem() == agenda)
                    .map(AgendaOption::getValue)
                    .toList();
        }

        @Nested
        class Success {

            @Test
            @DisplayName("설문 답이 모두 같으면 세 안건을 투표 없이 결정하고 방을 PLANNING으로 넘긴다")
            void resolvesUnanimousSubjects() {
                givenNoSurveyAgendas();
                givenAnswers(
                        answer(me, 600_000L, StayType.HOTEL, Pace.NORMAL, NOW.minusHours(2)),
                        answer(jieun, 604_000L, StayType.HOTEL, Pace.NORMAL, NOW.minusHours(1)));

                agendaService.openSurveyAgendas(room, NOW);

                assertThat(savedAgendas)
                        .extracting(AgendaItem::getSubject)
                        .containsExactly(
                                AgendaSubject.BUDGET, AgendaSubject.STAY, AgendaSubject.PACE);
                assertThat(savedAgendas)
                        .allSatisfy(agenda -> {
                            assertThat(agenda.getStatus()).isEqualTo(AgendaStatus.RESOLVED);
                            assertThat(agenda.getDeadlineAt()).isEqualTo(NOW);
                            assertThat(agenda.getTotalVoter()).isEqualTo(2);
                        });
                // 604,000원은 만원 반올림하면 60만원이라 같은 답으로 본다
                assertThat(optionValues(saved(AgendaSubject.BUDGET))).containsExactly("600000");
                assertThat(room.getStatus()).isEqualTo(RoomStatus.PLANNING);
            }

            @Test
            @DisplayName("예산만 다르면 예산 안건만 낮은 순 선지로 투표를 열고 방은 SURVEYING에 남는다")
            void opensVotingForConflictingSubject() {
                givenNoSurveyAgendas();
                givenAnswers(
                        answer(me, 1_000_000L, StayType.HOTEL, Pace.NORMAL, NOW.minusHours(2)),
                        answer(jieun, 600_000L, StayType.HOTEL, Pace.NORMAL, NOW.minusHours(1)));

                agendaService.openSurveyAgendas(room, NOW);

                AgendaItem budget = saved(AgendaSubject.BUDGET);
                assertThat(budget.getStatus()).isEqualTo(AgendaStatus.VOTING);
                assertThat(budget.getDeadlineAt()).isEqualTo(NOW.plusDays(4));
                assertThat(optionValues(budget)).containsExactly("600000", "1000000");
                assertThat(saved(AgendaSubject.STAY).getStatus()).isEqualTo(AgendaStatus.RESOLVED);
                assertThat(saved(AgendaSubject.PACE).getStatus()).isEqualTo(AgendaStatus.RESOLVED);
                assertThat(room.getStatus()).isEqualTo(RoomStatus.SURVEYING);
            }

            @Test
            @DisplayName("설문 마감 뒤에 낸 응답은 판정에서 빠진다")
            void ignoresAnswersAfterClose() {
                givenNoSurveyAgendas();
                givenAnswers(
                        answer(me, 600_000L, StayType.HOTEL, Pace.NORMAL, NOW.minusHours(2)),
                        answer(jieun, 1_000_000L, StayType.SOCIAL, Pace.HURRY, NOW.plusHours(1)));

                agendaService.openSurveyAgendas(room, NOW);

                assertThat(savedAgendas)
                        .allSatisfy(agenda ->
                                assertThat(agenda.getStatus()).isEqualTo(AgendaStatus.RESOLVED));
                assertThat(room.getStatus()).isEqualTo(RoomStatus.PLANNING);
            }

            @Test
            @DisplayName("출발일 00시가 지났으면 안건을 만들지 않는다")
            void skipsAfterDepartureDay() {
                LocalDateTime departureStart = LocalDateTime.of(2027, 7, 1, 0, 0);
                ReflectionTestUtils.setField(room, "surveyClosedAt", departureStart);
                givenNoSurveyAgendas();

                agendaService.openSurveyAgendas(room, departureStart);

                verify(agendaItemRepository, never()).save(any());
                verifyNoInteractions(surveyResponseService);
                assertThat(room.getStatus()).isEqualTo(RoomStatus.SURVEYING);
            }

            @Test
            @DisplayName("설문 마감으로 투표가 열리면 열린 투표 수를 담아 설문 마감 한 줄만 남긴다")
            void recordsSurveyClosedWithVotingCount() {
                givenNoSurveyAgendas();
                givenAnswers(
                        answer(me, 1_000_000L, StayType.HOTEL, Pace.NORMAL, NOW.minusHours(2)),
                        answer(jieun, 600_000L, StayType.SOCIAL, Pace.NORMAL, NOW.minusHours(1)));

                agendaService.openSurveyAgendas(room, NOW);

                verify(agendaChangePublisher).surveyClosed(room, 2);
                verifyNoMoreInteractions(agendaChangePublisher);
            }

            @Test
            @DisplayName("세 항목이 모두 만장일치면 설문 마감 다음에 조율 완료를 시스템을 행위자로 남긴다")
            void recordsCoordinationCompletedWhenAllUnanimous() {
                givenNoSurveyAgendas();
                givenAnswers(
                        answer(me, 600_000L, StayType.HOTEL, Pace.NORMAL, NOW.minusHours(2)),
                        answer(jieun, 600_000L, StayType.HOTEL, Pace.NORMAL, NOW.minusHours(1)));

                agendaService.openSurveyAgendas(room, NOW);

                InOrder order = inOrder(agendaChangePublisher);
                order.verify(agendaChangePublisher).surveyClosed(room, 0);
                order.verify(agendaChangePublisher).coordinationCompleted(room, null);
                verifyNoMoreInteractions(agendaChangePublisher);
            }

            @Test
            @DisplayName("출발일 00시가 지나 안건을 열지 않아도 설문 마감은 남긴다")
            void recordsSurveyClosedAfterDepartureDay() {
                LocalDateTime departureStart = LocalDateTime.of(2027, 7, 1, 0, 0);
                ReflectionTestUtils.setField(room, "surveyClosedAt", departureStart);
                givenNoSurveyAgendas();

                agendaService.openSurveyAgendas(room, departureStart);

                verify(agendaChangePublisher).surveyClosed(room, 0);
                verifyNoMoreInteractions(agendaChangePublisher);
            }
        }

        @Nested
        class Failure {

            @Test
            @DisplayName("설문이 마감되지 않은 방이면 호출 버그로 보고 막는다")
            void rejectsOpenSurvey() {
                ReflectionTestUtils.setField(room, "surveyClosedAt", null);

                assertThatThrownBy(() -> agendaService.openSurveyAgendas(room, NOW))
                        .isInstanceOf(IllegalStateException.class);
                verify(agendaItemRepository, never()).save(any());
            }

            @Test
            @DisplayName("이미 설문 안건이 열린 방이면 두 번 열지 않는다")
            void rejectsAlreadyOpened() {
                given(
                                agendaItemRepository.findTopByRoomIdAndSubjectOrderByRoundDesc(
                                        ROOM_ID, AgendaSubject.BUDGET))
                        .willReturn(Optional.of(votingAgenda(NOW.plusDays(3))));

                assertThatThrownBy(() -> agendaService.openSurveyAgendas(room, NOW))
                        .isInstanceOf(IllegalStateException.class);
                verify(agendaItemRepository, never()).save(any());
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

            @Test
            @DisplayName("선지를 추가하면 방 활동 기록 없이 늘어난 선지 목록을 vote.updated로 알린다")
            void publishesVoteUpdatedAfterAdding() {
                AgendaItem budget = openAgenda(AgendaSubject.BUDGET);
                AgendaOption low = budget(budget, 600_000L, 0, null);
                givenLockedRoomAndMember(me);
                givenAgenda(budget);
                givenAddable(budget, "1230000");
                givenOptions(budget, low);

                AgendaDetailResponse response = add(budget, 1_234_567L);

                verify(agendaChangePublisher).voteUpdated(ROOM_ID, response);
                verifyNoMoreInteractions(agendaChangePublisher);
            }
        }

        @Nested
        class Failure {

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

            @Test
            @DisplayName("선지를 지우면 방 활동 기록 없이 줄어든 선지 목록을 vote.updated로 알린다")
            void publishesVoteUpdatedAfterDeleting() {
                AgendaItem budget = openBudget();
                AgendaOption mine = budget(budget, 1_200_000L, 1, me);
                givenLockedRoomAndMember(me);
                givenAgenda(budget);
                givenOption(budget, mine);
                givenOthersVoteCount(mine, 0);

                AgendaDetailResponse response = delete(budget, mine);

                verify(agendaChangePublisher).voteUpdated(ROOM_ID, response);
                verifyNoMoreInteractions(agendaChangePublisher);
            }
        }

        @Nested
        class Failure {

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

    @Nested
    class OpenNextRound {

        @BeforeEach
        void giveRoomId() {
            ReflectionTestUtils.setField(room, "id", ROOM_ID);
        }

        // NOW - 8일에 열려 NOW - 1일에 미결 마감된 1회차 설문 안건
        private AgendaItem expiredAgenda(AgendaSubject subject) {
            AgendaItem expired = agenda(subject, NOW.minusDays(8), NOW.minusDays(1));
            expired.expire();
            return expired;
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

        private void givenLatestRound(AgendaItem latest) {
            given(
                            agendaItemRepository.findTopByRoomIdAndSubjectAndTitleOrderByRoundDesc(
                                    ROOM_ID, latest.getSubject(), latest.getTitle()))
                    .willReturn(Optional.of(latest));
        }

        // 저장하면 DB가 id와 생성 시각을 채운다
        private void givenSavedNextRound() {
            given(roomMemberService.countActiveMembers(ROOM_ID)).willReturn(3L);
            given(agendaItemRepository.saveAndFlush(any()))
                    .willAnswer(invocation -> {
                        AgendaItem next = invocation.getArgument(0);
                        withId(next);
                        ReflectionTestUtils.setField(next, "createdAt", NOW);
                        return next;
                    });
        }

        // saveAll로 저장된 선지를 모은다
        private List<AgendaOption> captureSavedOptions() {
            List<AgendaOption> saved = new ArrayList<>();
            given(agendaOptionRepository.saveAll(any()))
                    .willAnswer(invocation -> {
                        Iterable<AgendaOption> options = invocation.getArgument(0);
                        options.forEach(saved::add);
                        return saved;
                    });
            return saved;
        }

        private SurveyResponse answer(RoomMember member, long budget) {
            return SurveyResponse.createForSurveying(
                    member,
                    budget,
                    StayType.HOTEL,
                    Pace.NORMAL,
                    List.of(ActivityCode.FOOD),
                    "없음",
                    clockAt(NOW));
        }

        private AgendaDetailResponse open(AgendaItem agenda) {
            return agendaService.openNextRound(ROOM_ID, agenda.getId(), USER_ID);
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
            @DisplayName("다음 회차는 round + 1, 남은 멤버 수만큼의 자격자로 열리고 설문 금액과 직전 회차 추가 선지로 선지를 만든다")
            void opensNextBudgetRound() {
                AgendaItem previous = expiredAgenda(AgendaSubject.BUDGET);
                AgendaOption proposed = budget(previous, 800_000L, 2, jieun);
                givenLockedRoomAndMember(me);
                givenAgenda(previous);
                givenLatestRound(previous);
                givenSavedNextRound();
                given(agendaOptionRepository.findAllProposedByActiveMembers(previous.getId()))
                        .willReturn(List.of(proposed));
                given(surveyResponseService.getActiveMemberResponses(ROOM_ID))
                        .willReturn(List.of(answer(me, 600_000L), answer(jieun, 1_000_000L)));
                List<AgendaOption> saved = captureSavedOptions();

                AgendaDetailResponse response = open(previous);

                assertThat(response.round()).isEqualTo(2);
                assertThat(response.status()).isEqualTo(AgendaStatus.VOTING);
                assertThat(response.totalVoter()).isEqualTo(3);
                assertThat(response.eligible()).isTrue();
                assertThat(saved)
                        .extracting(AgendaOption::getValue)
                        .containsExactly("600000", "800000", "1000000");
                assertThat(saved.get(1).getProposedBy()).isSameAs(jieun);
                assertThat(saved).allMatch(o -> o.getAgendaItem().getRound() == 2);
            }

            @Test
            @DisplayName("남은 멤버 중 이 항목에 답한 사람이 없으면 직전 회차 선지를 그대로 옮긴다")
            void copiesPreviousOptionsWithoutAnswers() {
                AgendaItem previous = expiredAgenda(AgendaSubject.STAY);
                AgendaOption hotel = withId(AgendaOption.stay(previous, StayType.HOTEL, 0));
                AgendaOption social = withId(AgendaOption.stay(previous, StayType.SOCIAL, 1));
                givenLockedRoomAndMember(me);
                givenAgenda(previous);
                givenLatestRound(previous);
                givenSavedNextRound();
                given(surveyResponseService.getActiveMemberResponses(ROOM_ID))
                        .willReturn(List.of());
                given(
                                agendaOptionRepository.findAllByAgendaItemIdOrderByDisplayIndexAsc(
                                        previous.getId()))
                        .willReturn(List.of(hotel, social));
                List<AgendaOption> saved = captureSavedOptions();

                open(previous);

                assertThat(saved)
                        .extracting(AgendaOption::getValue)
                        .containsExactly("HOTEL", "SOCIAL");
                assertThat(saved).allMatch(o -> o.getAgendaItem() != previous);
                // 숙소 안건이라 직전 회차 추가 선지는 조회하지 않는다
                verify(agendaOptionRepository, never()).findAllProposedByActiveMembers(any());
            }

            @Test
            @DisplayName("다음 회차를 열면 연 나를 행위자로 새 회차의 열림 기록을 남긴다")
            void recordsOpenedNextRound() {
                AgendaItem previous = expiredAgenda(AgendaSubject.STAY);
                givenLockedRoomAndMember(me);
                givenAgenda(previous);
                givenLatestRound(previous);
                givenSavedNextRound();
                given(surveyResponseService.getActiveMemberResponses(ROOM_ID))
                        .willReturn(List.of(answer(me, 600_000L)));
                captureSavedOptions();

                open(previous);

                ArgumentCaptor<AgendaItem> opened = ArgumentCaptor.forClass(AgendaItem.class);
                verify(agendaChangePublisher).opened(eq(me), opened.capture());
                assertThat(opened.getValue()).isNotSameAs(previous);
                assertThat(opened.getValue().getSubject()).isEqualTo(AgendaSubject.STAY);
                assertThat(opened.getValue().getRound()).isEqualTo(2);
                verifyNoMoreInteractions(agendaChangePublisher);
            }
        }

        @Nested
        class Failure {

            @Test
            @DisplayName("방이 SURVEYING·PLANNING이 아니면 INVALID_STATUS를 던지고 안건을 조회하지 않는다")
            void rejectsInvalidRoomStatus() {
                ReflectionTestUtils.setField(room, "status", RoomStatus.REVIEWING);
                givenLockedRoomAndMember(me);

                assertError(
                        () -> agendaService.openNextRound(ROOM_ID, 5L, USER_ID),
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
                        () -> agendaService.openNextRound(ROOM_ID, 5L, USER_ID),
                        AgendaErrorCode.AGENDA_NOT_FOUND);
            }

            @Test
            @DisplayName("최종 확정 안건이면 미결 마감이어도 AGENDA_NOT_REOPENABLE을 던진다")
            void rejectsFinalPlan() {
                AgendaItem finalPlan = expiredAgenda(AgendaSubject.FINAL_PLAN);
                givenLockedRoomAndMember(me);
                givenAgenda(finalPlan);

                assertError(() -> open(finalPlan), AgendaErrorCode.AGENDA_NOT_REOPENABLE);
                verify(agendaItemRepository, never()).saveAndFlush(any());
            }

            @Test
            @DisplayName("아직 투표 중인 설문 안건이면 AGENDA_NOT_REOPENABLE을 던진다")
            void rejectsVotingAgenda() {
                AgendaItem voting = votingAgenda(NOW.plusDays(1));
                givenLockedRoomAndMember(me);
                givenAgenda(voting);

                assertError(() -> open(voting), AgendaErrorCode.AGENDA_NOT_REOPENABLE);
                verify(agendaItemRepository, never()).saveAndFlush(any());
            }

            @Test
            @DisplayName("이미 다음 회차가 열렸으면 AGENDA_ALREADY_REOPENED를 던지고 data에 최신 회차 id를 담는다")
            void rejectsAlreadyReopened() {
                AgendaItem previous = expiredAgenda(AgendaSubject.BUDGET);
                AgendaItem latest = votingAgenda(NOW.plusDays(7));
                givenLockedRoomAndMember(me);
                givenAgenda(previous);
                givenLatestRound(latest);

                assertThatThrownBy(() -> open(previous))
                        .isInstanceOf(BusinessException.class)
                        .satisfies(e -> {
                            BusinessException be = (BusinessException) e;
                            assertThat(be.getErrorCode())
                                    .isEqualTo(AgendaErrorCode.AGENDA_ALREADY_REOPENED);
                            assertThat(be.getData()).isEqualTo(Map.of("agendaId", latest.getId()));
                        });
                verify(agendaItemRepository, never()).saveAndFlush(any());
            }

            @Test
            @DisplayName("저장 중 회차 유니크 제약에 걸리면 AGENDA_ALREADY_REOPENED로 바꿔 던지고 선지를 만들지 않는다")
            void convertsUniqueViolation() {
                AgendaItem previous = expiredAgenda(AgendaSubject.BUDGET);
                givenLockedRoomAndMember(me);
                givenAgenda(previous);
                givenLatestRound(previous);
                given(roomMemberService.countActiveMembers(ROOM_ID)).willReturn(3L);
                given(agendaItemRepository.saveAndFlush(any()))
                        .willThrow(new DataIntegrityViolationException("uk_agenda_item_round"));

                assertError(() -> open(previous), AgendaErrorCode.AGENDA_ALREADY_REOPENED);
                verifyNoInteractions(surveyResponseService);
                verify(agendaOptionRepository, never()).saveAll(any());
            }
        }
    }

    @Nested
    class OpenFinalReview {

        // 서비스가 저장한 선지와 표. 판정과 응답 조립이 저장소에서 다시 읽을 때 그대로 돌려준다
        private final List<AgendaOption> savedOptions = new ArrayList<>();
        private final List<Vote> savedVotes = new ArrayList<>();

        @BeforeEach
        void planningRoom() {
            ReflectionTestUtils.setField(room, "id", ROOM_ID);
            ReflectionTestUtils.setField(room, "status", RoomStatus.PLANNING);
        }

        private void givenLockedRoomAndMember(LocalDateTime now) {
            given(clock.instant()).willReturn(now.atZone(SEOUL).toInstant());
            given(clock.getZone()).willReturn(SEOUL);
            given(roomService.getForUpdate(ROOM_ID)).willReturn(room);
            given(roomMemberService.getActiveMember(ROOM_ID, USER_ID)).willReturn(me);
        }

        private void givenActiveMembers(long count) {
            given(roomMemberService.countActiveMembers(ROOM_ID)).willReturn(count);
        }

        private void givenPreviousFinalReview(AgendaItem previous) {
            given(
                            agendaItemRepository.findTopByRoomIdAndSubjectOrderByRoundDesc(
                                    ROOM_ID, AgendaSubject.FINAL_PLAN))
                    .willReturn(Optional.ofNullable(previous));
        }

        // 저장하면 DB처럼 id와 생성 시각을 채운다
        private void givenSaving() {
            given(agendaItemRepository.save(any(AgendaItem.class)))
                    .willAnswer(invocation -> {
                        AgendaItem agenda = withId(invocation.<AgendaItem>getArgument(0));
                        ReflectionTestUtils.setField(agenda, "createdAt", NOW);
                        return agenda;
                    });
            given(agendaOptionRepository.saveAll(anyList()))
                    .willAnswer(invocation -> {
                        List<AgendaOption> options = invocation.getArgument(0);
                        options.forEach(option -> savedOptions.add(withId(option)));
                        return options;
                    });
            given(voteRepository.save(any(Vote.class)))
                    .willAnswer(invocation -> {
                        Vote vote = withId(invocation.<Vote>getArgument(0));
                        savedVotes.add(vote);
                        return vote;
                    });
            given(voteRepository.findAllByAgendaItemId(any()))
                    .willAnswer(invocation -> List.copyOf(savedVotes));
            given(agendaOptionRepository.findAllByAgendaItemIdOrderByDisplayIndexAsc(any()))
                    .willAnswer(invocation -> List.copyOf(savedOptions));
        }

        private void assertOpenError(ErrorCode errorCode) {
            assertThatThrownBy(() -> agendaService.openFinalReview(ROOM_ID, USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(errorCode);
        }

        @Nested
        class Success {

            @Test
            @DisplayName("최종 확정 투표를 열면 1회차 안건과 동의·반대 선지를 만들고 내 동의 표를 넣은 뒤 방을 REVIEWING으로 바꾼다")
            void opensFirstRound() {
                givenLockedRoomAndMember(NOW);
                givenActiveMembers(2);
                givenPreviousFinalReview(null);
                givenSaving();

                AgendaDetailResponse response = agendaService.openFinalReview(ROOM_ID, USER_ID);

                assertThat(response.subject()).isEqualTo(AgendaSubject.FINAL_PLAN);
                assertThat(response.round()).isEqualTo(1);
                assertThat(response.status()).isEqualTo(AgendaStatus.VOTING);
                assertThat(response.totalVoter()).isEqualTo(2);
                assertThat(response.deadlineAt()).isEqualTo(NOW.plusDays(7));
                assertThat(response.options())
                        .extracting(OptionResult::value)
                        .containsExactly("AGREE", "REJECT");
                assertThat(response.votedCount()).isEqualTo(1);
                assertThat(response.myOptionId()).isEqualTo(response.options().get(0).optionId());
                assertThat(room.getStatus()).isEqualTo(RoomStatus.REVIEWING);
            }

            @Test
            @DisplayName("반대나 미결로 끝난 최종 확정이 있으면 그다음 회차로 연다")
            void opensNextRound() {
                AgendaItem previous = AgendaItem.openFinalReview(room, 1, 2, NOW.minusDays(3));
                previous.expire();
                givenLockedRoomAndMember(NOW);
                givenActiveMembers(2);
                givenPreviousFinalReview(previous);
                givenSaving();

                AgendaDetailResponse response = agendaService.openFinalReview(ROOM_ID, USER_ID);

                assertThat(response.round()).isEqualTo(2);
                assertThat(room.getStatus()).isEqualTo(RoomStatus.REVIEWING);
            }

            @Test
            @DisplayName("활동 멤버가 나 혼자면 내 동의 표로 바로 결정되어 방이 FINALIZED가 된다")
            void finalizesSoloRoom() {
                givenLockedRoomAndMember(NOW);
                givenActiveMembers(1);
                givenPreviousFinalReview(null);
                givenSaving();

                AgendaDetailResponse response = agendaService.openFinalReview(ROOM_ID, USER_ID);

                assertThat(response.status()).isEqualTo(AgendaStatus.RESOLVED);
                assertThat(room.getStatus()).isEqualTo(RoomStatus.FINALIZED);
                assertThat(room.getFinalizedAt()).isEqualTo(NOW);
            }

            @Test
            @DisplayName("최종 확정 투표를 열면 연 나를 행위자로 열림 기록을 남기고 아직 결정 기록은 남기지 않는다")
            void recordsOpenedFinalReview() {
                givenLockedRoomAndMember(NOW);
                givenActiveMembers(2);
                givenPreviousFinalReview(null);
                givenSaving();

                agendaService.openFinalReview(ROOM_ID, USER_ID);

                verify(agendaChangePublisher)
                        .opened(eq(me), argThat(a -> a.getSubject() == AgendaSubject.FINAL_PLAN));
                verifyNoMoreInteractions(agendaChangePublisher);
            }

            @Test
            @DisplayName("혼자인 방에서 최종 확정 투표를 열면 열림 기록 다음에 계획 확정 기록을 남긴다")
            void recordsOpenedThenFinalizedInSoloRoom() {
                givenLockedRoomAndMember(NOW);
                givenActiveMembers(1);
                givenPreviousFinalReview(null);
                givenSaving();

                agendaService.openFinalReview(ROOM_ID, USER_ID);

                InOrder order = inOrder(agendaChangePublisher);
                order.verify(agendaChangePublisher).opened(eq(me), any());
                order.verify(agendaChangePublisher).planFinalized(room, me);
                verifyNoMoreInteractions(agendaChangePublisher);
            }
        }

        @Nested
        class Failure {

            @ParameterizedTest
            @EnumSource(
                    value = RoomStatus.class,
                    names = "PLANNING",
                    mode = EnumSource.Mode.EXCLUDE)
            @DisplayName("방이 PLANNING이 아니면 INVALID_STATUS로 막고 아무것도 저장하지 않는다")
            void rejectsOutsidePlanning(RoomStatus status) {
                ReflectionTestUtils.setField(room, "status", status);
                givenLockedRoomAndMember(NOW);

                assertOpenError(CommonErrorCode.INVALID_STATUS);
                verify(agendaItemRepository, never()).save(any());
                assertThat(room.getStatus()).isEqualTo(status);
            }

            @Test
            @DisplayName("출발일 00시가 지나면 AGENDA_DEADLINE_PASSED로 막고 방은 PLANNING에 남는다")
            void rejectsAfterDepartureDay() {
                givenLockedRoomAndMember(LocalDateTime.of(2027, 7, 1, 0, 0));
                givenActiveMembers(2);
                givenPreviousFinalReview(null);

                assertOpenError(AgendaErrorCode.AGENDA_DEADLINE_PASSED);
                verify(agendaItemRepository, never()).save(any());
                assertThat(room.getStatus()).isEqualTo(RoomStatus.PLANNING);
            }
        }
    }
}
