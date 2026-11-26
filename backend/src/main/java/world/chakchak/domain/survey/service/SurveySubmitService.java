package world.chakchak.domain.survey.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.agenda.service.AgendaService;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.service.RoomMemberService;
import world.chakchak.domain.room.service.RoomService;
import world.chakchak.domain.survey.dto.SubmitSurveyRequest;
import world.chakchak.domain.survey.dto.SubmitSurveyResponse;
import world.chakchak.domain.survey.errorcode.SurveySubmitErrorCode;
import world.chakchak.global.error.BusinessException;
import world.chakchak.global.error.CommonErrorCode;

@Service
@RequiredArgsConstructor
public class SurveySubmitService {

    private final RoomService roomService;
    private final RoomMemberService roomMemberService;
    private final SurveyResponseService surveyResponseService;
    private final AgendaService agendaService;
    private final Clock clock;

    @Transactional(noRollbackFor = BusinessException.class)
    public SubmitSurveyResponse submit(Long roomId, Long userId, SubmitSurveyRequest request) {
        Room room = roomService.getForUpdate(roomId);
        RoomMember member = roomMemberService.getActiveMember(roomId, userId);
        if (room.getStatus() == RoomStatus.FINALIZED || room.getStatus() == RoomStatus.EXPIRED) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS);
        }
        if (surveyResponseService.hasResponse(member.getId())) {
            throw new BusinessException(SurveySubmitErrorCode.ALREADY_SUBMITTED);
        }

        // 조율 중(SURVEYING)이면 설문 마감 뒤에도 예산·숙소·이동 강도를 받는다. 지금 회차에는 넣지 않고 다음 회차(V-04) 선지에 쓴다
        boolean surveying = room.getStatus() == RoomStatus.SURVEYING;
        if (surveying
                && (request.budget() == null
                        || request.stayType() == null
                        || request.pace() == null)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (request.actPrefs().size() > 3
                || new HashSet<>(request.actPrefs()).size() != request.actPrefs().size()) {
            throw new BusinessException(SurveySubmitErrorCode.INVALID_ACTIVITY_COUNT);
        }
        if (!surveying
                && (request.budget() != null
                        || request.stayType() != null
                        || request.pace() != null)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        boolean afterDeadline =
                surveying
                        && room.getOpinionDeadlineAt() != null
                        && !now.isBefore(room.getOpinionDeadlineAt());

        boolean closed = false;
        if (afterDeadline && room.getSurveyClosedAt() == null) {
            closed = closeSurveyIfReady(room, roomId, now);
            if (room.getStatus() == RoomStatus.EXPIRED) {
                throw new BusinessException(CommonErrorCode.INVALID_STATUS);
            }
            if (closed) {
                agendaService.openSurveyAgendas(room, now);
            }
        }

        SubmitSurveyResponse saved = surveyResponseService.save(member, request, surveying);
        if (surveying && !afterDeadline && room.getSurveyClosedAt() == null) {
            LocalDateTime closeAt = LocalDateTime.now(clock);
            if (closeAt.isBefore(saved.submittedAt())) {
                closeAt = saved.submittedAt();
            }
            closed = closeSurveyIfReady(room, roomId, closeAt);
            if (closed) {
                agendaService.openSurveyAgendas(room, closeAt);
            }
        }
        return new SubmitSurveyResponse(saved.surveyId(), saved.submittedAt(), closed);
    }

    private boolean closeSurveyIfReady(Room room, Long roomId, LocalDateTime now) {
        return room.closeSurveyIfReady(
                roomMemberService.countActiveMembers(roomId),
                surveyResponseService.countActiveResponses(roomId),
                now);
    }
}
