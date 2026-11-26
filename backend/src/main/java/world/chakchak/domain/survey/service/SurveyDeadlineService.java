package world.chakchak.domain.survey.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.agenda.service.AgendaChangePublisher;
import world.chakchak.domain.agenda.service.AgendaService;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.service.RoomMemberService;
import world.chakchak.domain.room.service.RoomService;

/** 의견 마감 시각이 지난 방의 설문을 마감한다 (13-1). 안건을 여는 일만 {@link AgendaService}에 맡긴다. */
@Service
@RequiredArgsConstructor
public class SurveyDeadlineService {

    private final RoomService roomService;
    private final RoomMemberService roomMemberService;
    private final SurveyResponseService surveyResponseService;
    private final AgendaService agendaService;
    private final AgendaChangePublisher agendaChangePublisher;
    private final Clock clock;

    /** 의견 마감이 지났는데 설문이 마감되지 않은 방을 찾는다. */
    public List<Long> findRoomIdsPastOpinionDeadline() {
        return roomService.findRoomIdsPastOpinionDeadline(LocalDateTime.now(clock));
    }

    /**
     * 의견 마감이 지난 방의 설문을 마감하고 1회차 안건을 연다 (5-1). 제출이 0건이면 방이 EXPIRED가 된다.
     *
     * <p>잠금을 기다리는 사이 D-1이 됐으면 건드리지 않는다. 그 방은 최종 집계가 처리한다.
     */
    @Transactional
    public void closeOverdueSurvey(Long roomId) {
        Room room = roomService.getForUpdate(roomId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!AgendaItem.canOpenAt(room, now)) {
            return;
        }
        boolean wasSurveying = room.getStatus() == RoomStatus.SURVEYING;
        boolean closed =
                room.closeSurveyIfReady(
                        roomMemberService.countActiveMembers(roomId),
                        surveyResponseService.countActiveResponses(roomId),
                        now);
        if (closed) {
            agendaService.openSurveyAgendas(room, now);
        }
        // 제출이 0건이라 이번에 방이 만료됐다. 알리지 않으면 열려 있는 화면은 다시 불러오기 전까지 설문 중으로 보인다
        if (wasSurveying && room.getStatus() == RoomStatus.EXPIRED) {
            agendaChangePublisher.roomExpired(room);
        }
    }
}
