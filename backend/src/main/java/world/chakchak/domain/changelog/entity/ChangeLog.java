package world.chakchak.domain.changelog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.global.entity.BaseEntity;

@Entity
@Table(name = "change_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChangeLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_room_id", nullable = false, updatable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_room_member_id", updatable = false)
    private RoomMember actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 32, updatable = false)
    private EntityType entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_kind", nullable = false, length = 32, updatable = false)
    private EventKind eventKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_status", nullable = false, length = 16, updatable = false)
    private ActionStatus actionStatus;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String summary;

    private ChangeLog(
            Room room,
            RoomMember actor,
            EntityType entityType,
            Long entityId,
            EventKind eventKind,
            ActionStatus actionStatus,
            String summary) {
        this.room = room;
        this.actor = actor;
        this.entityType = entityType;
        this.entityId = entityId;
        this.eventKind = eventKind;
        this.actionStatus = actionStatus;
        this.summary = summary;
    }

    public static ChangeLog create(
            Room room,
            RoomMember actor,
            EntityType entityType,
            Long entityId,
            EventKind eventKind,
            ActionStatus actionStatus,
            String summary) {
        return new ChangeLog(room, actor, entityType, entityId, eventKind, actionStatus, summary);
    }
}
