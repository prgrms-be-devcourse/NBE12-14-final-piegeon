package world.chakchak.domain.agenda;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import world.chakchak.domain.agenda.service.AgendaDeadlineScheduler;
import world.chakchak.domain.agenda.service.AgendaService;

@ExtendWith(MockitoExtension.class)
class AgendaDeadlineSchedulerTest {

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

                scheduler.expireOverdueAgendas();

                verify(agendaService).expireOverdueAgendas(1L);
                verify(agendaService).expireOverdueAgendas(2L);
            }

            @Test
            @DisplayName("기한 지난 안건이 없으면 아무 방도 처리하지 않는다")
            void doesNothingWithoutOverdueRooms() {
                given(agendaService.findRoomIdsWithOverdueAgendas()).willReturn(List.of());

                scheduler.expireOverdueAgendas();

                verify(agendaService, never()).expireOverdueAgendas(any());
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

                scheduler.expireOverdueAgendas();

                verify(agendaService).expireOverdueAgendas(2L);
            }
        }
    }
}
