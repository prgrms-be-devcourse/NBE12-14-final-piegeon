package world.chakchak.domain.survey.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.survey.entity.SurveyResponse;
import world.chakchak.domain.survey.repository.SurveyResponseRepository;

/**
 * 설문 응답 조회. 안건 도메인이 설문 답으로 선지를 만들 때(V-04, 5-1) 쓴다.
 *
 * <p>TODO: 설문 담당 SurveyService가 생기면 이 메서드를 그쪽으로 옮긴다. 지금은 안건 도메인이 설문 repository를 직접 쓰지 않도록 최소한으로만
 * 둔다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SurveyService {

    private final SurveyResponseRepository surveyResponseRepository;

    /** 방에 남아 있는 멤버들의 설문 응답. 나간 멤버의 응답은 빠진다. */
    public List<SurveyResponse> getActiveMemberResponses(Long roomId) {
        return surveyResponseRepository.findAllByRoomMember_Room_IdAndRoomMember_LeftAtIsNull(
                roomId);
    }
}
