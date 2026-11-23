package world.chakchak.domain.survey.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SurveyStatusResponse(
        boolean closed,
        LocalDateTime opinionDeadlineAt,
        long activeMemberCount,
        long submittedCount,
        List<MemberStatus> members) {

    public record MemberStatus(
            Long memberId, String name, boolean submitted, LocalDateTime submittedAt) {}
}
