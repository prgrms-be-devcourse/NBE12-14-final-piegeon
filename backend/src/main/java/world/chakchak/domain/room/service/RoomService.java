package world.chakchak.domain.room.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import world.chakchak.domain.room.dto.CreateRoomRequest;
import world.chakchak.domain.room.dto.RoomCreatedResponse;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.errorcode.RoomErrorCode;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.schedule.service.ScheduleDayService;
import world.chakchak.global.error.BusinessException;

@Service
@RequiredArgsConstructor
public class RoomService {

    private static final int MAX_ATTEMPTS = 10;
    private static final String INVITE_CODE_CONSTRAINT = "uk_plan_room_invite_code";

    private final RoomRepository roomRepository;
    private final RoomMemberService roomMemberService;
    private final ScheduleDayService scheduleDayService;
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;

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
