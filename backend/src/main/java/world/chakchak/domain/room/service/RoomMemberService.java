package world.chakchak.domain.room.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
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

    @Transactional
    public RoomMember addMember(Room room, Long userId) {
        return roomMemberRepository.saveAndFlush(
                RoomMember.create(room, userService.getEntityById(userId), clock));
    }

    public RoomMember getActiveMember(Long roomId, Long userId) {
        RoomRepository.RoomMemberAccessProjection access =
                roomRepository
                        .findRoomAndMemberForAccess(roomId, userId)
                        .orElseThrow(() -> new BusinessException(RoomErrorCode.ROOM_NOT_FOUND));
        if (access.getActiveMember() == null) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        return access.getActiveMember();
    }

    public RoomMember getActiveMemberForStream(Long roomId, Long userId) {
        return getActiveMember(roomId, userId);
    }

    public boolean isActiveMember(Long roomId, Long memberId) {
        return roomMemberRepository.existsByRoom_IdAndIdAndLeftAtIsNull(roomId, memberId);
    }

    @Transactional
    public void advanceLastSeenAt(Long memberId, LocalDateTime seenUntil) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime boundedSeenUntil = seenUntil.isAfter(now) ? now : seenUntil;
        roomMemberRepository.advanceLastSeenAt(memberId, boundedSeenUntil, now);
    }

    /** 방에 남아 있는 멤버 수 (나간 멤버 제외). 장소 칸 계산(PlaceStage)처럼 인원 기준이 필요한 곳에서 쓴다. */
    public long countActiveMembers(Long roomId) {
        return roomMemberRepository.countByRoom_IdAndLeftAtIsNull(roomId);
    }

    /** 방에 남아 있는 멤버의 id와 이름, 들어온 순서대로. 한 쿼리로 이름까지 가져온다. (장소 상세 P-10) */
    public List<RoomMemberRepository.ActiveRoomMemberSummary> getActiveMemberSummaries(
            Long roomId) {
        return roomMemberRepository.findAllActiveMemberSummariesByRoomId(roomId);
    }
}
