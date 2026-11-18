package world.chakchak.domain.room.service;

import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.errorcode.RoomErrorCode;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.user.service.UserService;
import world.chakchak.global.error.BusinessException;
import world.chakchak.global.error.CommonErrorCode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomMemberService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserService userService;
    private final Clock clock;

    @Transactional
    public RoomMember addCreator(Room room, Long userId) {
        return roomMemberRepository.save(
                RoomMember.create(room, userService.getEntityById(userId), clock));
    }

    /**
     * 방에 속한 API가 service 첫 줄에서 부른다. 로그인한 사용자가 이 방의 활동 멤버(나가지 않은 멤버)인지 확인하고 그 멤버를 돌려준다.
     *
     * <p>멤버 행이 있으면 FK 때문에 방도 반드시 있으므로, 방 존재 확인은 멤버가 없을 때만 한다. 정상 요청은 쿼리 1개로 끝난다.
     *
     * @throws BusinessException 방이 없으면 ROOM_NOT_FOUND, 멤버가 아니거나 나간 멤버면 FORBIDDEN
     */
    public RoomMember getActiveMember(Long roomId, Long userId) {
        RoomMember member =
                roomMemberRepository
                        .findByRoom_IdAndUser_Id(roomId, userId)
                        .orElseThrow(() ->
                                roomRepository.existsById(roomId)
                                        ? new BusinessException(CommonErrorCode.FORBIDDEN)
                                        : new BusinessException(RoomErrorCode.ROOM_NOT_FOUND));
        if (member.getLeftAt() != null) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return member;
    }
}
