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
import world.chakchak.domain.room.service.RoomMemberService;
import world.chakchak.domain.room.service.RoomService;
import world.chakchak.domain.survey.dto.SubmitSurveyRequest;
import world.chakchak.domain.survey.dto.SubmitSurveyResponse;
import world.chakchak.domain.survey.dto.SurveyMeResponse;
import world.chakchak.domain.survey.entity.ActivityCode;
import world.chakchak.domain.survey.entity.Pace;
import world.chakchak.domain.survey.entity.StayType;
import world.chakchak.domain.survey.entity.SurveyResponse;
import world.chakchak.domain.survey.errorcode.SurveyFetchErrorCode;
import world.chakchak.domain.survey.errorcode.SurveySubmitErrorCode;
import world.chakchak.domain.survey.repository.SurveyResponseRepository;
import world.chakchak.global.error.BusinessException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SurveyResponseService {

    private final SurveyResponseRepository surveyResponseRepository;
    private final Clock clock;
    private final RoomService roomService;
    private final RoomMemberService roomMemberService;

    public List<SurveyResponse> getActiveMemberResponses(Long roomId) {
        return surveyResponseRepository.findAllByRoomMember_Room_IdAndRoomMember_LeftAtIsNull(
                roomId);
    }

    @Transactional(readOnly = true)
    public SurveyMeResponse getMine(Long roomId, Long userId) {
        roomService.checkExists(roomId);
        RoomMember member = roomMemberService.getActiveMember(roomId, userId);
        SurveyResponse response =
                surveyResponseRepository
                        .findByRoomMember_Id(member.getId())
                        .orElseThrow(
                                () -> new BusinessException(SurveyFetchErrorCode.SURVEY_NOT_FOUND));
        return new SurveyMeResponse(
                response.getId(),
                response.getBudget(),
                option(response.getStayType()),
                option(response.getPace()),
                response.getActPrefs().stream().map(SurveyResponseService::option).toList(),
                response.getAvoidNote(),
                response.getSubmittedAt());
    }

    private static SurveyMeResponse.Option option(StayType value) {
        return value == null ? null : new SurveyMeResponse.Option(value.name(), value.getLabel());
    }

    private static SurveyMeResponse.Option option(Pace value) {
        return value == null ? null : new SurveyMeResponse.Option(value.name(), value.getLabel());
    }

    private static SurveyMeResponse.Option option(ActivityCode value) {
        return new SurveyMeResponse.Option(value.name(), value.getLabel());
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
