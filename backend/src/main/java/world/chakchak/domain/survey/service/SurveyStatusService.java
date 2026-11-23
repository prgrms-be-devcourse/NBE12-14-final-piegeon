package world.chakchak.domain.survey.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.service.RoomMemberService;
import world.chakchak.domain.survey.dto.SurveyStatusResponse;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SurveyStatusService {

    private final RoomMemberService roomMemberService;
    private final RoomMemberRepository roomMemberRepository;

    public SurveyStatusResponse getStatus(Long roomId, Long userId) {
        var member = roomMemberService.getActiveMember(roomId, userId);
        var room = member.getRoom();
        List<RoomMemberRepository.ActiveRoomMemberSummary> summaries =
                roomMemberService.getActiveMemberSummaries(roomId);
        boolean closed = room.getSurveyClosedAt() != null;
        List<SurveyStatusResponse.MemberStatus> members =
                summaries.stream()
                        .map(summary ->
                                new SurveyStatusResponse.MemberStatus(
                                        summary.getMemberId(),
                                        summary.getName(),
                                        summary.getSurveySubmitted(),
                                        summary.getSurveySubmitted()
                                                ? summary.getSubmittedAt()
                                                : null))
                        .toList();
        long submittedCount =
                summaries.stream()
                        .filter(RoomMemberRepository.ActiveRoomMemberSummary::getSurveySubmitted)
                        .count();
        return new SurveyStatusResponse(
                closed, room.getOpinionDeadlineAt(), members.size(), submittedCount, members);
    }
}
