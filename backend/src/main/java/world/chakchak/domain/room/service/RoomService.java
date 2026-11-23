package world.chakchak.domain.room.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import world.chakchak.domain.changelog.service.ChangeLogService;
import world.chakchak.domain.room.dto.CreateRoomRequest;
import world.chakchak.domain.room.dto.InvitePreviewResponse;
import world.chakchak.domain.room.dto.JoinRoomResponse;
import world.chakchak.domain.room.dto.NotJoinableReason;
import world.chakchak.domain.room.dto.RoomCreatedResponse;
import world.chakchak.domain.room.dto.RoomDetailResponse;
import world.chakchak.domain.room.dto.RoomListResponse;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.errorcode.RoomErrorCode;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.schedule.service.ScheduleDayService;
import world.chakchak.global.error.BusinessException;

@Service
@RequiredArgsConstructor
public class RoomService {

    private static final int MAX_ATTEMPTS = 10;
    private static final String INVITE_CODE_CONSTRAINT = "uk_plan_room_invite_code";

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomMemberService roomMemberService;
    private final ScheduleDayService scheduleDayService;
    private final ChangeLogService changeLogService;
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;

    @Transactional(readOnly = true)
    public RoomListResponse getMyRooms(Long userId) {
        return new RoomListResponse(roomRepository.findMyRoomSummaries(userId));
    }

    @Transactional(readOnly = true)
    public InvitePreviewResponse getInvitePreview(String inviteCode, Long userId) {
        Room room =
                roomRepository
                        .findByInviteCode(inviteCode)
                        .orElseThrow(
                                () -> new BusinessException(RoomErrorCode.INVITE_CODE_NOT_FOUND));

        NotJoinableReason notJoinableReason = resolveNotJoinableReason(room.getId(), userId);
        int memberCount =
                Math.toIntExact(roomMemberRepository.countByRoom_IdAndLeftAtIsNull(room.getId()));
        return InvitePreviewResponse.from(room, memberCount, notJoinableReason);
    }

    @Transactional
    public JoinRoomResponse joinByInviteCode(String inviteCode, Long userId) {
        Room room =
                roomRepository
                        .findByInviteCodeForUpdate(inviteCode)
                        .orElseThrow(
                                () -> new BusinessException(RoomErrorCode.INVITE_CODE_NOT_FOUND));

        roomMemberRepository
                .findByRoom_IdAndUser_Id(room.getId(), userId)
                .ifPresent(existingMember -> {
                    if (existingMember.getLeftAt() == null) {
                        throw new BusinessException(RoomErrorCode.ALREADY_MEMBER);
                    }
                    throw new BusinessException(RoomErrorCode.REJOIN_NOT_ALLOWED);
                });

        RoomMember member = roomMemberService.addMember(room, userId);
        changeLogService.recordMemberJoined(room, member);
        boolean surveyRequired = room.getStatus() != RoomStatus.FINALIZED;
        return new JoinRoomResponse(room.getId(), member.getId(), surveyRequired);
    }

    private NotJoinableReason resolveNotJoinableReason(Long roomId, Long userId) {
        if (userId == null) {
            return NotJoinableReason.LOGIN_REQUIRED;
        }

        return roomMemberRepository
                .findByRoom_IdAndUser_Id(roomId, userId)
                .map(member ->
                        member.getLeftAt() == null
                                ? NotJoinableReason.ALREADY_MEMBER
                                : NotJoinableReason.REJOIN_NOT_ALLOWED)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public RoomDetailResponse getRoomDetail(Long roomId, Long userId) {
        RoomMember activeMember = roomMemberService.getActiveMember(roomId, userId);
        Room room = activeMember.getRoom();

        List<RoomDetailResponse.MemberItem> members =
                roomMemberRepository.findAllActiveMemberSummariesByRoomId(room.getId()).stream()
                        .map(member ->
                                new RoomDetailResponse.MemberItem(
                                        member.getMemberId(),
                                        member.getName(),
                                        member.getJoinedAt(),
                                        member.getSurveySubmitted()))
                        .toList();
        return RoomDetailResponse.from(room, members);
    }

    @Transactional
    public void markSeen(Long roomId, Long userId, LocalDateTime seenUntil) {
        RoomMember member = roomMemberService.getActiveMember(roomId, userId);
        roomMemberService.advanceLastSeenAt(member.getId(), seenUntil);
    }

    public RoomCreatedResponse create(Long userId, CreateRoomRequest request) {
        validateDates(request.startDate(), request.endDate());
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String inviteCode = UUID.randomUUID().toString().substring(0, 8);
            if (roomRepository.existsByInviteCode(inviteCode)) {
                continue;
            }
            try {
                return transaction.execute(
                        status -> createInTransaction(userId, request, inviteCode));
            } catch (DataIntegrityViolationException e) {
                if (!isInviteCodeCollision(e)) {
                    throw e;
                }
            }
        }
        throw new IllegalStateException("Unable to generate a unique invite code");
    }

    /**
     * 방 행을 잠그고 돌려준다. 방에 속한 쓰기 API가 트랜잭션의 첫 쿼리로 부른다. (명세 1-4)
     *
     * <p>잠금은 트랜잭션이 끝날 때 풀리므로 트랜잭션 밖에서 부르면 잡자마자 풀려 의미가 없다. 그래서 MANDATORY로 두어 호출하는 쪽에 트랜잭션이 없으면 예외를
     * 던진다.
     *
     * @throws BusinessException 방이 없으면 ROOM_NOT_FOUND
     */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = BusinessException.class)
    public Room getForUpdate(Long roomId) {
        return roomRepository
                .findByIdForUpdate(roomId)
                .orElseThrow(() -> new BusinessException(RoomErrorCode.ROOM_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public void checkExists(Long roomId) {
        if (!roomRepository.existsById(roomId)) {
            throw new BusinessException(RoomErrorCode.ROOM_NOT_FOUND);
        }
    }

    private RoomCreatedResponse createInTransaction(
            Long userId, CreateRoomRequest request, String inviteCode) {
        Room room =
                roomRepository.saveAndFlush(
                        Room.create(
                                request.name(),
                                request.destination(),
                                request.startDate(),
                                request.endDate(),
                                inviteCode,
                                RoomStatus.SURVEYING,
                                clock));
        RoomMember creator = roomMemberService.addCreator(room, userId);
        scheduleDayService.createForTrip(room);
        return RoomCreatedResponse.from(room, creator);
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now(clock);
        if (!startDate.atStartOfDay().isAfter(LocalDateTime.now(clock).plusHours(24))) {
            throw new BusinessException(RoomErrorCode.DEPARTURE_TOO_SOON);
        }
        if (!startDate.isBefore(today.plusYears(1))) {
            throw new BusinessException(RoomErrorCode.DEPARTURE_TOO_FAR);
        }
        long tripDays = endDate.toEpochDay() - startDate.toEpochDay() + 1;
        if (tripDays <= 0 || tripDays > 31) {
            throw new BusinessException(RoomErrorCode.INVALID_DATE_RANGE);
        }
    }

    private boolean isInviteCodeCollision(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof ConstraintViolationException violation) {
                return INVITE_CODE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }
}
