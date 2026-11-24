package world.chakchak.domain.room.service;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.agenda.service.AgendaService;
import world.chakchak.domain.changelog.event.RoomMemberLeftEvent;
import world.chakchak.domain.changelog.service.ChangeLogService;
import world.chakchak.domain.place.service.PlaceCandidateService;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.errorcode.RoomErrorCode;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.survey.service.SurveyResponseService;
import world.chakchak.global.error.BusinessException;

@Service
@RequiredArgsConstructor
public class RoomLeaveService {

    private final RoomRepository roomRepository;
    private final RoomMemberService roomMemberService;
    private final SurveyResponseService surveyResponseService;
    private final AgendaService agendaService;
    private final PlaceCandidateService placeCandidateService;
    private final ChangeLogService changeLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public void leave(Long roomId, Long userId) {
        Room room =
                roomRepository
                        .findByIdForUpdate(roomId)
                        .orElseThrow(() -> new BusinessException(RoomErrorCode.ROOM_NOT_FOUND));
        RoomMember member = roomMemberService.getActiveMember(roomId, userId);
        LocalDateTime now = LocalDateTime.now(clock);

        surveyResponseService.deleteForMember(member.getId());
        member.leave(now);
        boolean surveyClosed =
                room.getStatus() == RoomStatus.SURVEYING
                        && room.closeSurveyIfReady(
                                roomMemberService.countActiveMembers(roomId),
                                surveyResponseService.countActiveResponses(roomId),
                                now);
        agendaService.memberLeft(room, member, now);
        // 설문이 이번 퇴장으로 마감됐으면 1회차 안건을 연다 (5-1). memberLeft보다 먼저 열면 나간 멤버만큼 total_voter가 한 번 더 줄어든다
        if (surveyClosed) {
            agendaService.openSurveyAgendas(room, now);
        }
        placeCandidateService.deleteReactionsByMember(member.getId());
        eventPublisher.publishEvent(new RoomMemberLeftEvent(roomId, member.getId()));
        changeLogService.recordMemberLeft(room, member);
    }
}
