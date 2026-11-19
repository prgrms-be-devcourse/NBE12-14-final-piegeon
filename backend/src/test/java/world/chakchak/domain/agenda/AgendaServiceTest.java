package world.chakchak.domain.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import world.chakchak.domain.agenda.dto.AgendaDetailResponse;
import world.chakchak.domain.agenda.dto.AgendaDetailResponse.OptionResult;
import world.chakchak.domain.agenda.dto.AgendaListResponse;
import world.chakchak.domain.agenda.dto.MemberRef;
import world.chakchak.domain.agenda.dto.OptionRef;
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
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.service.RoomMemberService;
import world.chakchak.domain.survey.entity.Pace;
import world.chakchak.domain.survey.entity.StayType;
import world.chakchak.domain.user.entity.User;
import world.chakchak.global.error.BusinessException;
import world.chakchak.global.error.CommonErrorCode;

@ExtendWith(MockitoExtension.class)
class AgendaServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 6, 1, 12, 0);
    private static final Long ROOM_ID = 1L;
    private static final Long USER_ID = 100L;

    @Mock private RoomMemberService roomMemberService;
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

        @Nested
        class Success {

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
}
