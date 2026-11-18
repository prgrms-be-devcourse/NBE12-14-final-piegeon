package world.chakchak.domain.changelog.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.changelog.entity.ActionStatus;
import world.chakchak.domain.changelog.entity.ChangeLog;
import world.chakchak.domain.changelog.entity.EntityType;
import world.chakchak.domain.changelog.entity.EventKind;
import world.chakchak.domain.changelog.errorcode.ChangeLogErrorCode;
import world.chakchak.domain.changelog.repository.ChangeLogRepository;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.global.error.BusinessException;

@Service
@RequiredArgsConstructor
public class ChangeLogService {

    private final EntityManager entityManager;
    private final ChangeLogRepository changeLogRepository;

    /** 호출자의 트랜잭션에서 방 활동 한 건을 저장하고, 방 행 잠금으로 재연결 ID 순서를 보장한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public ChangeLog record(
            Long roomId,
            Long actorRoomMemberId,
            EntityType entityType,
            Long entityId,
            EventKind eventKind,
            ActionStatus actionStatus,
            String summary) {
        Room room = entityManager.find(Room.class, roomId, LockModeType.PESSIMISTIC_WRITE);
        if (room == null) {
            throw new BusinessException(ChangeLogErrorCode.ROOM_NOT_FOUND);
        }

        RoomMember actor = findActorInRoom(roomId, actorRoomMemberId);
        return changeLogRepository.save(
                ChangeLog.create(
                        room, actor, entityType, entityId, eventKind, actionStatus, summary));
    }

    private RoomMember findActorInRoom(Long roomId, Long actorRoomMemberId) {
        if (actorRoomMemberId == null) {
            return null;
        }

        List<RoomMember> members =
                entityManager
                        .createQuery(
                                "select member from RoomMember member "
                                        + "where member.id = :memberId and member.room.id = :roomId",
                                RoomMember.class)
                        .setParameter("memberId", actorRoomMemberId)
                        .setParameter("roomId", roomId)
                        .setMaxResults(1)
                        .getResultList();
        if (members.isEmpty()) {
            throw new BusinessException(ChangeLogErrorCode.ACTOR_NOT_IN_ROOM);
        }
        return members.getFirst();
    }
}
