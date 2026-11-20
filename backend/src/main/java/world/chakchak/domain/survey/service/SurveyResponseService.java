package world.chakchak.domain.survey.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.survey.repository.SurveyResponseRepository;

@Service
@RequiredArgsConstructor
public class SurveyResponseService {

    private final SurveyResponseRepository surveyResponseRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteForMember(Long memberId) {
        surveyResponseRepository.deleteByRoomMember_Id(memberId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long countActiveResponses(Long roomId) {
        return surveyResponseRepository.countByRoomMember_Room_IdAndRoomMember_LeftAtIsNull(roomId);
    }
}
