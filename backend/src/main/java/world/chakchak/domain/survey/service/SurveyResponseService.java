package world.chakchak.domain.survey.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.service.RoomService;
import world.chakchak.domain.survey.dto.SubmitSurveyRequest;
import world.chakchak.domain.survey.dto.SubmitSurveyResponse;
import world.chakchak.domain.survey.entity.ActivityCode;
import world.chakchak.domain.survey.entity.SurveyResponse;
import world.chakchak.domain.survey.errorcode.SurveySubmitErrorCode;
import world.chakchak.domain.survey.repository.SurveyResponseRepository;
import world.chakchak.global.error.BusinessException;
import world.chakchak.global.error.CommonErrorCode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SurveyResponseService {

    private final SurveyResponseRepository surveyResponseRepository;
    private final RoomService roomService;
    private final RoomMemberRepository roomMemberRepository;
    private final Clock clock;

    /** 방에 남아 있는 멤버들의 설문 응답. 나간 멤버의 응답은 빠진다. */
    public List<SurveyResponse> getActiveMemberResponses(Long roomId) {
        return surveyResponseRepository.findAllByRoomMember_Room_IdAndRoomMember_LeftAtIsNull(
                roomId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteForMember(Long memberId) {
        surveyResponseRepository.deleteByRoomMember_Id(memberId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long countActiveResponses(Long roomId) {
        return surveyResponseRepository.countByRoomMember_Room_IdAndRoomMember_LeftAtIsNull(roomId);
    }

    @Transactional
    public SubmitSurveyResult submit(Long roomId, Long userId, SubmitSurveyRequest request) {
        Room room = roomService.getForUpdate(roomId);
        RoomMember member =
                roomMemberRepository
                        .findByRoom_IdAndUser_Id(roomId, userId)
                        .filter(existing -> existing.getLeftAt() == null)
                        .orElseThrow(() -> new BusinessException(CommonErrorCode.FORBIDDEN));
        if (surveyResponseRepository.findByRoomMember_Id(member.getId()).isPresent()) {
            throw new BusinessException(SurveySubmitErrorCode.ALREADY_SUBMITTED);
        }
        if (room.getStatus() == RoomStatus.FINALIZED) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS);
        }
        boolean surveying = room.getStatus() == RoomStatus.SURVEYING;
        if (!surveying
                && (request.budget() != null
                        || request.stayType() != null
                        || request.pace() != null)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        boolean late =
                room.getOpinionDeadlineAt() != null && !now.isBefore(room.getOpinionDeadlineAt());
        if (request.actPrefs() == null
                || request.actPrefs().size() < 1
                || request.actPrefs().size() > 3) {
            throw new BusinessException(SurveySubmitErrorCode.INVALID_ACTIVITY_COUNT);
        }
        if (new HashSet<>(request.actPrefs()).size() != request.actPrefs().size()) {
            throw new BusinessException(SurveySubmitErrorCode.INVALID_ACTIVITY_COUNT);
        }
        if (request.avoidNote() != null && request.avoidNote().length() > 100) {
            throw new BusinessException(SurveySubmitErrorCode.INVALID_INPUT);
        }
        if (surveying
                && (request.budget() == null
                        || request.stayType() == null
                        || request.pace() == null)) {
            throw new BusinessException(SurveySubmitErrorCode.INVALID_INPUT);
        }
        if (request.budget() != null && (request.budget() < 1 || request.budget() > 99_999_999L)) {
            throw new BusinessException(SurveySubmitErrorCode.INVALID_INPUT);
        }
        boolean closedNow = false;
        if (surveying && late && room.getSurveyClosedAt() == null) {
            closedNow =
                    room.closeSurveyIfReady(
                            roomMemberRepository.countByRoom_IdAndLeftAtIsNull(roomId),
                            surveyResponseRepository
                                    .countByRoomMember_Room_IdAndRoomMember_LeftAtIsNull(roomId),
                            now);
            if (closedNow && now.toLocalDate().isBefore(room.getStartDate().minusDays(1))) {
                // TODO: 설문 마감 시 기존 응답 집계로 1차 안건을 오픈한다.
            }
        }
        List<ActivityCode> activities = request.actPrefs();
        SurveyResponse response;
        try {
            response =
                    surveying
                            ? SurveyResponse.createForSurveying(
                                    member,
                                    request.budget(),
                                    request.stayType(),
                                    request.pace(),
                                    activities,
                                    request.avoidNote(),
                                    clock)
                            : SurveyResponse.createForPlanning(
                                    member, activities, request.avoidNote(), clock);
            response = surveyResponseRepository.saveAndFlush(response);
        } catch (DataIntegrityViolationException exception) {
            if (hasMemberUniqueViolation(exception)) {
                throw new BusinessException(SurveySubmitErrorCode.ALREADY_SUBMITTED);
            }
            throw exception;
        }
        if (surveying && !late && room.getSurveyClosedAt() == null) {
            closedNow =
                    room.closeSurveyIfReady(
                            roomMemberRepository.countByRoom_IdAndLeftAtIsNull(roomId),
                            surveyResponseRepository
                                    .countByRoomMember_Room_IdAndRoomMember_LeftAtIsNull(roomId),
                            now);
            if (closedNow && now.toLocalDate().isBefore(room.getStartDate().minusDays(1))) {
                // TODO: 설문 마감 시 기존 응답 집계로 1차 안건을 오픈한다.
            }
        }
        return new SubmitSurveyResult(
                new SubmitSurveyResponse(response.getId(), response.getSubmittedAt(), closedNow),
                late);
    }

    private boolean hasMemberUniqueViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "plan_room_member_id".equalsIgnoreCase(violation.getConstraintName()))
                return true;
        }
        return false;
    }

    public record SubmitSurveyResult(SubmitSurveyResponse data, boolean late) {}
}
