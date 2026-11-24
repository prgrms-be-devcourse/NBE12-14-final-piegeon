package world.chakchak.domain.room.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SummaryResponse(
        LocalDateTime since,
        LocalDateTime until,
        long totalChanges,
        List<SummaryGroup> groups,
        NeedsMyAction needsMyAction) {

    public record SummaryGroup(Kind kind, long count, List<String> lines) {}

    public record NeedsMyAction(boolean surveyNotSubmitted, List<UnvotedAgenda> unvotedAgendas) {}

    public record UnvotedAgenda(Long agendaId, String title, LocalDateTime deadlineAt) {}

    public enum Kind {
        MEMBER_JOINED,
        MEMBER_LEFT,
        SURVEY_CLOSED,
        COORDINATION_COMPLETED,
        AGENDA_OPENED,
        AGENDA_RESOLVED,
        AGENDA_EXPIRED,
        PLACE_REGISTERED,
        PLACE_DELETED,
        PLACE_STAGE_CHANGED,
        PLACE_CONFIRMED,
        SCHEDULE_CHANGED,
        PLAN_FINALIZED
    }
}
