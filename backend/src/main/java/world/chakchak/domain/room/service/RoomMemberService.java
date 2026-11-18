package world.chakchak.domain.room.service;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.user.service.UserService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomMemberService {

    private final RoomMemberRepository roomMemberRepository;
    private final UserService userService;
    private final Clock clock;

    @Transactional
    public RoomMember addCreator(Room room, Long userId) {
        return roomMemberRepository.save(
                RoomMember.create(room, userService.getEntityById(userId), clock));
    }
}
