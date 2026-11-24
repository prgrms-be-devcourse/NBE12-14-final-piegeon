package world.chakchak.domain.agenda.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 기한이 지난 안건을 미결로 끝내는 스케줄러 (13-2).
 *
 * <p>방마다 별도 트랜잭션으로 처리해, 한 방이 실패해도 다른 방은 처리된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgendaDeadlineScheduler {

    private final AgendaService agendaService;

    @Scheduled(cron = "${app.scheduler.agenda-deadline-cron}", zone = "Asia/Seoul")
    public void expireOverdueAgendas() {
        List<Long> roomIds = agendaService.findRoomIdsWithOverdueAgendas();
        for (Long roomId : roomIds) {
            try {
                agendaService.expireOverdueAgendas(roomId);
            } catch (RuntimeException e) {
                log.error("안건 마감 처리 실패: roomId={}", roomId, e);
            }
        }
    }
}
