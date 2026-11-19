package world.chakchak.domain.changelog.dto;

import java.time.LocalDateTime;
import world.chakchak.domain.changelog.entity.ActionStatus;
import world.chakchak.domain.changelog.entity.ChangeLog;
import world.chakchak.domain.changelog.entity.EntityType;
import world.chakchak.domain.changelog.entity.EventKind;

public record ChangeLogItemResponse(
        Long id,
        Long roomId,
        Long actorMemberId,
        String actorName,
        EntityType entityType,
        Long entityId,
        EventKind eventKind,
        ActionStatus actionStatus,
        String summary,
        LocalDateTime createdAt) {

    public static ChangeLogItemResponse from(ChangeLog changeLog) {
        Long actorMemberId = changeLog.getActor() == null ? null : changeLog.getActor().getId();
        String actorName =
                changeLog.getActor() == null
                        ? "시스템"
                        : changeLog.getActor().getUser().getName();
        return new ChangeLogItemResponse(
                changeLog.getId(),
                changeLog.getRoom().getId(),
                actorMemberId,
                actorName,
                changeLog.getEntityType(),
                changeLog.getEntityId(),
                changeLog.getEventKind(),
                changeLog.getActionStatus(),
                changeLog.getSummary(),
                changeLog.getCreatedAt());
    }
}
