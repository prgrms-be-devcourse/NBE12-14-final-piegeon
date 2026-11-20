package world.chakchak.domain.survey.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.survey.entity.SurveyResponse;
import world.chakchak.domain.survey.repository.SurveyResponseRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SurveyResponseService {

    private final SurveyResponseRepository surveyResponseRepository;

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
}
