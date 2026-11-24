package world.chakchak.domain.changelog.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.changelog.dto.ChangeLogItemResponse;
import world.chakchak.domain.changelog.entity.ActionStatus;
import world.chakchak.domain.changelog.entity.ChangeLog;
import world.chakchak.domain.changelog.entity.EntityType;
import world.chakchak.domain.changelog.entity.EventKind;
import world.chakchak.domain.changelog.event.RoomEvent;
import world.chakchak.domain.changelog.repository.ChangeLogRepository;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;

@Service
@RequiredArgsConstructor
public class ChangeLogService {

    private final ChangeLogRepository changeLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordMemberJoined(Room room, RoomMember member) {
        record(
                room,
                member,
                EntityType.MEMBER,
                member.getId(),
                EventKind.MEMBER_JOINED,
                ActionStatus.CREATE,
                member.getUser().getName() + "님이 합류했어요");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordMemberLeft(Room room, RoomMember member) {
        ChangeLog changeLog =
                ChangeLog.create(
                        room,
                        member,
                        EntityType.MEMBER,
                        member.getId(),
                        EventKind.MEMBER_LEFT,
                        ActionStatus.DELETE,
                        member.getUser().getName() + "님이 나갔어요");
        ChangeLog saved = changeLogRepository.save(changeLog);
        ChangeLogItemResponse item = ChangeLogItemResponse.from(saved);
        eventPublisher.publishEvent(new RoomEvent(room.getId(), saved.getId(), "activity", item));
        eventPublisher.publishEvent(new RoomEvent(room.getId(), null, "member.left", item));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
            Room room,
            RoomMember actor,
            EntityType entityType,
            Long entityId,
            EventKind eventKind,
            ActionStatus actionStatus,
            String summary) {
        ChangeLog changeLog =
                ChangeLog.create(
                        room, actor, entityType, entityId, eventKind, actionStatus, summary);
        ChangeLog saved = changeLogRepository.save(changeLog);
        ChangeLogItemResponse item = ChangeLogItemResponse.from(saved);
        eventPublisher.publishEvent(new RoomEvent(room.getId(), saved.getId(), "activity", item));
    }
}
