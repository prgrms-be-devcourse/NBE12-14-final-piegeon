package world.chakchak.domain.changelog.dto;

import java.time.LocalDateTime;
import world.chakchak.domain.changelog.entity.ActionStatus;
import world.chakchak.domain.changelog.entity.ChangeLog;
import world.chakchak.domain.changelog.entity.EntityType;

public record ChangeLogItemResponse(
        Long logId,
        ActorResponse actor,
        EntityType entityType,
        Long entityId,
        ActionStatus action,
        String summary,
        LocalDateTime createdAt) {

    public static ChangeLogItemResponse from(ChangeLog changeLog) {
        ActorResponse actor =
                changeLog.getActor() == null
                        ? null
                        : new ActorResponse(
                                changeLog.getActor().getId(),
                                changeLog.getActor().getUser().getName());
        return new ChangeLogItemResponse(
                changeLog.getId(),
                actor,
                changeLog.getEntityType(),
                changeLog.getEntityId(),
                changeLog.getActionStatus(),
                changeLog.getSummary(),
                changeLog.getCreatedAt());
    }

    public record ActorResponse(Long memberId, String name) {}
}
