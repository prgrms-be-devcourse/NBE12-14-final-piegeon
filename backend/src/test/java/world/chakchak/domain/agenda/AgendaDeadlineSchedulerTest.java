package world.chakchak.domain.agenda;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import world.chakchak.domain.agenda.service.AgendaDeadlineScheduler;
import world.chakchak.domain.agenda.service.AgendaService;
import world.chakchak.domain.survey.service.SurveyDeadlineService;

@ExtendWith(MockitoExtension.class)
class AgendaDeadlineSchedulerTest {

    @Mock private SurveyDeadlineService surveyDeadlineService;
    @Mock private AgendaService agendaService;

    @InjectMocks private AgendaDeadlineScheduler scheduler;

    @Nested
    class Update {

        @Nested
        class Success {

            @Test
            @DisplayName("기한 지난 안건이 있는 방을 방마다 따로 처리한다")
            void expiresEachRoom() {
                given(agendaService.findRoomIdsWithOverdueAgendas()).willReturn(List.of(1L, 2L));

                scheduler.runDeadlineJobs();

                verify(agendaService).expireOverdueAgendas(1L);
                verify(agendaService).expireOverdueAgendas(2L);
            }

            @Test
            @DisplayName("설문 마감, 안건 마감, D-1 최종 집계 순서로 실행한다")
            void runsJobsInOrder() {
                given(surveyDeadlineService.findRoomIdsPastOpinionDeadline())
                        .willReturn(List.of(1L));
                given(agendaService.findRoomIdsWithOverdueAgendas()).willReturn(List.of(2L));
                given(agendaService.findRoomIdsAtFinalTally()).willReturn(List.of(3L));

                scheduler.runDeadlineJobs();

                InOrder order = inOrder(surveyDeadlineService, agendaService);
                order.verify(surveyDeadlineService).closeOverdueSurvey(1L);
                order.verify(agendaService).expireOverdueAgendas(2L);
                order.verify(agendaService).runFinalTally(3L);
            }

            @Test
            @DisplayName("처리할 방이 없으면 아무 방도 처리하지 않는다")
            void doesNothingWithoutRooms() {
                given(surveyDeadlineService.findRoomIdsPastOpinionDeadline()).willReturn(List.of());
                given(agendaService.findRoomIdsWithOverdueAgendas()).willReturn(List.of());
                given(agendaService.findRoomIdsAtFinalTally()).willReturn(List.of());

                scheduler.runDeadlineJobs();

                verify(surveyDeadlineService, never()).closeOverdueSurvey(any());
                verify(agendaService, never()).expireOverdueAgendas(any());
                verify(agendaService, never()).runFinalTally(any());
            }
        }

        @Nested
        class Failure {

            @Test
            @DisplayName("한 방의 처리가 실패해도 다음 방을 처리한다")
            void continuesAfterRoomFailure() {
                given(agendaService.findRoomIdsWithOverdueAgendas()).willReturn(List.of(1L, 2L));
                willThrow(new IllegalStateException("실패"))
                        .given(agendaService)
                        .expireOverdueAgendas(1L);

                scheduler.runDeadlineJobs();

                verify(agendaService).expireOverdueAgendas(2L);
            }

            @Test
            @DisplayName("한 작업의 대상 조회가 실패해도 다음 작업을 실행한다")
            void continuesAfterQueryFailure() {
                given(surveyDeadlineService.findRoomIdsPastOpinionDeadline())
                        .willThrow(new IllegalStateException("실패"));
                given(agendaService.findRoomIdsAtFinalTally()).willReturn(List.of(3L));

                scheduler.runDeadlineJobs();

                verify(agendaService).runFinalTally(3L);
            }
        }
    }
}
