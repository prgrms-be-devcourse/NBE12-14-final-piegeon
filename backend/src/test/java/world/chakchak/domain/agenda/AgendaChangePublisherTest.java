package world.chakchak.domain.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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
import world.chakchak.domain.agenda.dto.AgendaDetailResponse;
import world.chakchak.domain.agenda.dto.AgendaDetailResponse.OptionResult;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.agenda.entity.AgendaOption;
import world.chakchak.domain.agenda.entity.AgendaStatus;
import world.chakchak.domain.agenda.entity.AgendaSubject;
import world.chakchak.domain.agenda.entity.FinalPlanChoice;
import world.chakchak.domain.agenda.event.RoomStatusChangedEvent;
import world.chakchak.domain.agenda.event.VoteUpdatedEvent;
import world.chakchak.domain.agenda.event.VoteUpdatedEvent.OptionVoteCount;
import world.chakchak.domain.agenda.service.AgendaChangePublisher;
import world.chakchak.domain.changelog.entity.ActionStatus;
import world.chakchak.domain.changelog.entity.EntityType;
import world.chakchak.domain.changelog.entity.EventKind;
import world.chakchak.domain.changelog.event.RoomEvent;
import world.chakchak.domain.changelog.service.ChangeLogService;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.user.entity.User;

/** 안건 도메인이 남기는 방 활동 기록의 종류·대상·행위·요약 문구와, 기록 없이 보내는 vote.updated·room.status_changed를 확인한다. */
@ExtendWith(MockitoExtension.class)
class AgendaChangePublisherTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 6, 1, 12, 0);
    private static final Long ROOM_ID = 1L;

    @Mock private ChangeLogService changeLogService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private AgendaChangePublisher agendaChangePublisher;

    private Room room;
    private RoomMember me;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.minusDays(3).atZone(SEOUL).toInstant(), SEOUL);
        room =
                Room.create(
                        "여름 여행",
                        "부산",
                        LocalDate.of(2027, 7, 1),
                        LocalDate.of(2027, 7, 3),
                        "ABCD1234",
                        RoomStatus.SURVEYING,
                        clock);
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
        me = RoomMember.create(room, User.create("a@example.com", "민수", "hash"), clock);
        ReflectionTestUtils.setField(me, "id", 10L);
    }

    private AgendaItem agenda(AgendaSubject subject, int round) {
        AgendaItem agenda =
                AgendaItem.create(room, subject, round, "안건 제목", 2, NOW, NOW.plusDays(3));
        ReflectionTestUtils.setField(agenda, "id", 5L);
        return agenda;
    }

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("안건이 열리면 연 멤버를 행위자로 AGENDA_OPENED를 CREATE로 남기고 요약에 주제와 회차를 적는다")
            void recordsOpened() {
                agendaChangePublisher.opened(me, agenda(AgendaSubject.BUDGET, 2));

                verify(changeLogService)
                        .record(
                                room,
                                me,
                                EntityType.AGENDA,
                                5L,
                                EventKind.AGENDA_OPENED,
                                ActionStatus.CREATE,
                                "예산 2회차 투표가 열렸어요");
            }

            @Test
            @DisplayName("설문 안건이 결정되면 AGENDA_RESOLVED를 CONFIRM으로 남기고 요약에 결정된 선지를 적는다")
            void recordsResolved() {
                AgendaItem budget = agenda(AgendaSubject.BUDGET, 1);

                agendaChangePublisher.resolved(
                        me, budget, AgendaOption.budget(budget, 300_000L, 0, null));

                verify(changeLogService)
                        .record(
                                room,
                                me,
                                EntityType.AGENDA,
                                5L,
                                EventKind.AGENDA_RESOLVED,
                                ActionStatus.CONFIRM,
                                "예산 1회차 결정: 30만원");
            }

            @Test
            @DisplayName("최종 확정이 반대로 결정되면 요약에 최종 확정과 반대를 적는다")
            void recordsFinalPlanRejected() {
                AgendaItem finalPlan = agenda(AgendaSubject.FINAL_PLAN, 1);

                agendaChangePublisher.resolved(
                        me, finalPlan, AgendaOption.finalPlan(finalPlan, FinalPlanChoice.REJECT));

                verify(changeLogService)
                        .record(
                                room,
                                me,
                                EntityType.AGENDA,
                                5L,
                                EventKind.AGENDA_RESOLVED,
                                ActionStatus.CONFIRM,
                                "최종 확정 1회차 결정: 반대");
            }

            @Test
            @DisplayName("안건이 미결로 끝나면 시스템 행위로 AGENDA_EXPIRED를 UPDATE로 남긴다")
            void recordsExpired() {
                agendaChangePublisher.expired(null, agenda(AgendaSubject.STAY, 1));

                verify(changeLogService)
                        .record(
                                room,
                                null,
                                EntityType.AGENDA,
                                5L,
                                EventKind.AGENDA_EXPIRED,
                                ActionStatus.UPDATE,
                                "숙소 타입 1회차: 정하지 못하고 마감됐어요");
            }

            @Test
            @DisplayName("설문이 마감되면 시스템 행위로 방을 대상으로 SURVEY_CLOSED를 남기고 요약에 열린 투표 수를 적는다")
            void recordsSurveyClosedWithVoting() {
                agendaChangePublisher.surveyClosed(room, 2);

                verify(changeLogService)
                        .record(
                                room,
                                null,
                                EntityType.PLAN_ROOM,
                                ROOM_ID,
                                EventKind.SURVEY_CLOSED,
                                ActionStatus.UPDATE,
                                "설문이 마감돼 투표 2건이 열렸어요");
            }

            @Test
            @DisplayName("열린 투표 없이 설문이 마감되면 요약에 마감만 적는다")
            void recordsSurveyClosedWithoutVoting() {
                agendaChangePublisher.surveyClosed(room, 0);

                verify(changeLogService)
                        .record(
                                room,
                                null,
                                EntityType.PLAN_ROOM,
                                ROOM_ID,
                                EventKind.SURVEY_CLOSED,
                                ActionStatus.UPDATE,
                                "설문이 마감됐어요");
            }

            @Test
            @DisplayName("설문 세 항목이 모두 정해지면 방을 대상으로 COORDINATION_COMPLETED를 UPDATE로 남긴다")
            void recordsCoordinationCompleted() {
                agendaChangePublisher.coordinationCompleted(room, me);

                verify(changeLogService)
                        .record(
                                room,
                                me,
                                EntityType.PLAN_ROOM,
                                ROOM_ID,
                                EventKind.COORDINATION_COMPLETED,
                                ActionStatus.UPDATE,
                                "예산·숙소 타입·이동 강도가 모두 정해졌어요");
            }

            @Test
            @DisplayName("최종 확정이 동의로 끝나면 방을 대상으로 PLAN_FINALIZED를 CONFIRM으로 남긴다")
            void recordsPlanFinalized() {
                agendaChangePublisher.planFinalized(room, me);

                verify(changeLogService)
                        .record(
                                room,
                                me,
                                EntityType.PLAN_ROOM,
                                ROOM_ID,
                                EventKind.PLAN_FINALIZED,
                                ActionStatus.CONFIRM,
                                "여행 계획이 확정됐어요");
            }

            @Test
            @DisplayName("표 수가 바뀌면 기록 없이 id 없는 vote.updated로 안건 id, 투표한 사람 수, 선지별 표 수를 보낸다")
            void publishesVoteUpdated() {
                AgendaDetailResponse detail =
                        new AgendaDetailResponse(
                                5L,
                                AgendaSubject.BUDGET,
                                1,
                                "예산을 정해주세요",
                                AgendaStatus.VOTING,
                                NOW.plusDays(3),
                                3,
                                2,
                                true,
                                21L,
                                List.of(
                                        new OptionResult(21L, "30만원", "300000", 2, List.of(), null),
                                        new OptionResult(
                                                22L, "50만원", "500000", 0, List.of(), null)));

                agendaChangePublisher.voteUpdated(ROOM_ID, detail);

                ArgumentCaptor<RoomEvent> captor = ArgumentCaptor.forClass(RoomEvent.class);
                verify(eventPublisher).publishEvent(captor.capture());
                RoomEvent event = captor.getValue();
                assertThat(event.roomId()).isEqualTo(ROOM_ID);
                assertThat(event.changeLogId()).isNull();
                assertThat(event.name()).isEqualTo("vote.updated");
                assertThat(event.data())
                        .isEqualTo(
                                new VoteUpdatedEvent(
                                        5L,
                                        2,
                                        List.of(
                                                new OptionVoteCount(21L, 2),
                                                new OptionVoteCount(22L, 0))));
            }

            @Test
            @DisplayName("D-1에 방이 만료되면 기록 없이 id 없는 room.status_changed로 EXPIRED를 보낸다")
            void publishesRoomExpired() {
                agendaChangePublisher.roomExpired(room);

                ArgumentCaptor<RoomEvent> captor = ArgumentCaptor.forClass(RoomEvent.class);
                verify(eventPublisher).publishEvent(captor.capture());
                RoomEvent event = captor.getValue();
                assertThat(event.roomId()).isEqualTo(ROOM_ID);
                assertThat(event.changeLogId()).isNull();
                assertThat(event.name()).isEqualTo("room.status_changed");
                assertThat(event.data()).isEqualTo(new RoomStatusChangedEvent(RoomStatus.EXPIRED));
                verifyNoInteractions(changeLogService);
            }
        }
    }
}
