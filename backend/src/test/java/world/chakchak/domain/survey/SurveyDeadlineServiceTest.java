package world.chakchak.domain.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import world.chakchak.domain.agenda.service.AgendaService;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.service.RoomMemberService;
import world.chakchak.domain.room.service.RoomService;
import world.chakchak.domain.survey.service.SurveyDeadlineService;
import world.chakchak.domain.survey.service.SurveyResponseService;

@ExtendWith(MockitoExtension.class)
class SurveyDeadlineServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 6, 1, 12, 0);
    // 출발일 2027-07-01의 전날 00:00
    private static final LocalDateTime D_MINUS_1 = LocalDateTime.of(2027, 6, 30, 0, 0);
    private static final Long ROOM_ID = 1L;

    @Mock private RoomService roomService;
    @Mock private RoomMemberService roomMemberService;
    @Mock private SurveyResponseService surveyResponseService;
    @Mock private AgendaService agendaService;
    @Mock private Clock clock;

    @InjectMocks private SurveyDeadlineService surveyDeadlineService;

    private Room room;

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
                        Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));
        ReflectionTestUtils.setField(room, "id", ROOM_ID);
    }

    private void givenNow(LocalDateTime now) {
        given(clock.instant()).willReturn(now.atZone(SEOUL).toInstant());
        given(clock.getZone()).willReturn(SEOUL);
    }

    private void givenLockedRoomAt(LocalDateTime now) {
        given(roomService.getForUpdate(ROOM_ID)).willReturn(room);
        givenNow(now);
    }

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("의견 마감이 지난 방을 지금 시각으로 찾는다")
            void findsRoomsWithCurrentTime() {
                givenNow(NOW);
                given(roomService.findRoomIdsPastOpinionDeadline(NOW)).willReturn(List.of(ROOM_ID));

                assertThat(surveyDeadlineService.findRoomIdsPastOpinionDeadline())
                        .containsExactly(ROOM_ID);
            }
        }
    }

    @Nested
    class Update {

        @Nested
        class Success {

            @Test
            @DisplayName("의견 마감이 지났고 제출이 있으면 미제출자가 있어도 설문을 마감하고 안건을 연다")
            void closesSurveyAndOpensAgendas() {
                LocalDateTime deadline = room.getOpinionDeadlineAt();
                givenLockedRoomAt(deadline);
                given(roomMemberService.countActiveMembers(ROOM_ID)).willReturn(3L);
                given(surveyResponseService.countActiveResponses(ROOM_ID)).willReturn(2L);

                surveyDeadlineService.closeOverdueSurvey(ROOM_ID);

                assertThat(room.getSurveyClosedAt()).isEqualTo(deadline);
                verify(agendaService).openSurveyAgendas(room, deadline);
            }

            @Test
            @DisplayName("의견 마감이 지났는데 제출이 0건이면 방이 EXPIRED가 되고 안건을 열지 않는다")
            void expiresRoomWithoutResponses() {
                givenLockedRoomAt(room.getOpinionDeadlineAt());
                given(roomMemberService.countActiveMembers(ROOM_ID)).willReturn(2L);
                given(surveyResponseService.countActiveResponses(ROOM_ID)).willReturn(0L);

                surveyDeadlineService.closeOverdueSurvey(ROOM_ID);

                assertThat(room.getStatus()).isEqualTo(RoomStatus.EXPIRED);
                assertThat(room.getSurveyClosedAt()).isNull();
                verifyNoInteractions(agendaService);
            }
        }

        @Nested
        class Failure {

            @Test
            @DisplayName("잠금을 기다리는 사이 D-1이 됐으면 설문을 마감하지 않고 최종 집계에 맡긴다")
            void skipsAtDayBeforeDeparture() {
                givenLockedRoomAt(D_MINUS_1);

                surveyDeadlineService.closeOverdueSurvey(ROOM_ID);

                assertThat(room.getSurveyClosedAt()).isNull();
                assertThat(room.getStatus()).isEqualTo(RoomStatus.SURVEYING);
                verifyNoInteractions(surveyResponseService, agendaService);
            }

            @Test
            @DisplayName("잠금을 기다리는 사이 설문이 이미 마감됐으면 안건을 다시 열지 않는다")
            void doesNotReopenAgendasWhenAlreadyClosed() {
                LocalDateTime deadline = room.getOpinionDeadlineAt();
                ReflectionTestUtils.setField(room, "surveyClosedAt", deadline.minusMinutes(1));
                givenLockedRoomAt(deadline);
                given(roomMemberService.countActiveMembers(ROOM_ID)).willReturn(2L);
                given(surveyResponseService.countActiveResponses(ROOM_ID)).willReturn(2L);

                surveyDeadlineService.closeOverdueSurvey(ROOM_ID);

                verify(agendaService, never()).openSurveyAgendas(any(), any());
            }
        }
    }
}
