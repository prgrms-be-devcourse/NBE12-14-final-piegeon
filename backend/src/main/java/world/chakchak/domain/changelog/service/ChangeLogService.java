package world.chakchak.domain.changelog.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.changelog.entity.ActionStatus;
import world.chakchak.domain.changelog.entity.ChangeLog;
import world.chakchak.domain.changelog.entity.EntityType;
import world.chakchak.domain.changelog.entity.EventKind;
import world.chakchak.domain.changelog.repository.ChangeLogRepository;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;

@Service
@RequiredArgsConstructor
public class ChangeLogService {

    private final ChangeLogRepository changeLogRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordMemberJoined(Room room, RoomMember member) {
        String summary = member.getUser().getName() + "님이 합류했어요";
        ChangeLog changeLog =
                ChangeLog.create(
                        room,
                        member,
                        EntityType.MEMBER,
                        member.getId(),
                        EventKind.MEMBER_JOINED,
                        ActionStatus.CREATE,
                        summary);
        changeLogRepository.save(changeLog);
    }
}
