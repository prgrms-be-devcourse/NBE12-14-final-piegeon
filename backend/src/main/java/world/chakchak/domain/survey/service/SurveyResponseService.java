package world.chakchak.domain.survey.service;

import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.survey.dto.SubmitSurveyRequest;
import world.chakchak.domain.survey.dto.SubmitSurveyResponse;
import world.chakchak.domain.survey.entity.SurveyResponse;
import world.chakchak.domain.survey.errorcode.SurveySubmitErrorCode;
import world.chakchak.domain.survey.repository.SurveyResponseRepository;
import world.chakchak.global.error.BusinessException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SurveyResponseService {

    private final SurveyResponseRepository surveyResponseRepository;
    private final Clock clock;

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

    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = BusinessException.class)
    public SubmitSurveyResponse save(
            RoomMember member, SubmitSurveyRequest request, boolean surveying) {
        SurveyResponse response =
                surveying
                        ? SurveyResponse.createForSurveying(
                                member,
                                request.budget(),
                                request.stayType(),
                                request.pace(),
                                request.actPrefs(),
                                request.avoidNote(),
                                clock)
                        : SurveyResponse.createForPlanning(
                                member, request.actPrefs(), request.avoidNote(), clock);
        try {
            response = surveyResponseRepository.saveAndFlush(response);
        } catch (DataIntegrityViolationException exception) {
            if (hasMemberUniqueViolation(exception)) {
                throw new BusinessException(SurveySubmitErrorCode.ALREADY_SUBMITTED);
            }
            throw exception;
        }
        return new SubmitSurveyResponse(response.getId(), response.getSubmittedAt(), false);
    }

    public boolean hasResponse(Long memberId) {
        return surveyResponseRepository.findByRoomMember_Id(memberId).isPresent();
    }

    private boolean hasMemberUniqueViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "uk_survey_response_room_member"
                            .equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
