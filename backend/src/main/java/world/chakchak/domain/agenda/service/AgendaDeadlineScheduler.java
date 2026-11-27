package world.chakchak.domain.agenda.service;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import world.chakchak.domain.survey.service.SurveyDeadlineService;

/**
 * 기한으로 움직이는 작업을 매분 차례로 실행하는 스케줄러. 설문 마감(13-1), 안건 마감(13-2), D-DAY 최종 집계 순서다.
 *
 * <p>방마다 별도 트랜잭션으로 처리해, 한 방이 실패해도 다른 방은 처리된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgendaDeadlineScheduler {

    private final SurveyDeadlineService surveyDeadlineService;
    private final AgendaService agendaService;

    @Scheduled(cron = "${app.scheduler.agenda-deadline-cron}", zone = "Asia/Seoul")
    public void runDeadlineJobs() {
        forEachRoom(
                "설문 마감",
                surveyDeadlineService::findRoomIdsPastOpinionDeadline,
                surveyDeadlineService::closeOverdueSurvey);
        forEachRoom(
                "안건 마감",
                agendaService::findRoomIdsWithOverdueAgendas,
                agendaService::expireOverdueAgendas);
        forEachRoom(
                "D-DAY 최종 집계",
                agendaService::findRoomIdsAtFinalTally,
                agendaService::runFinalTally);
    }

    private void forEachRoom(String job, Supplier<List<Long>> findRoomIds, Consumer<Long> process) {
        List<Long> roomIds;
        try {
            roomIds = findRoomIds.get();
        } catch (RuntimeException e) {
            log.error("{} 대상 조회 실패", job, e);
            return;
        }
        for (Long roomId : roomIds) {
            try {
                process.accept(roomId);
            } catch (RuntimeException e) {
                log.error("{} 처리 실패: roomId={}", job, roomId, e);
            }
        }
    }
}
