package world.chakchak.domain.room.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import world.chakchak.global.entity.BaseEntity;

@Entity
@Table(name = "plan_room")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 50)
    private String destination;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "invite_code", nullable = false, unique = true, length = 8)
    private String inviteCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status;

    @Column(name = "opinion_deadline_at")
    private LocalDateTime opinionDeadlineAt;

    @Column(name = "survey_closed_at")
    private LocalDateTime surveyClosedAt;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    private Room(
            String name,
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            String inviteCode,
            RoomStatus status,
            Clock clock) {
        this.name = name;
        this.destination = destination;
        this.startDate = startDate;
        this.endDate = endDate;
        this.inviteCode = inviteCode;
        this.status = status;
        this.opinionDeadlineAt = LocalDateTime.now(clock).plusDays(3);
    }

    public static Room create(
            String name,
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            String inviteCode,
            RoomStatus status,
            Clock clock) {
        return new Room(name, destination, startDate, endDate, inviteCode, status, clock);
    }

    /** 설문 항목(예산·숙소·이동 강도)이 모두 결정돼 계획 단계로 넘어간다. (5-2) */
    public void startPlanning() {
        requireStatus(RoomStatus.SURVEYING);
        this.status = RoomStatus.PLANNING;
    }

    /** 최종 확정 투표가 동의로 결정됐다. 이후 모든 수정이 막힌다. (7-1, 10-1) */
    public void finalizePlan(LocalDateTime now) {
        requireStatus(RoomStatus.REVIEWING);
        this.status = RoomStatus.FINALIZED;
        this.finalizedAt = now;
    }

    /** 최종 확정 투표가 반대로 끝났거나 미결 마감돼 계획 단계로 돌아간다. (7-1, 10-1) */
    public void returnToPlanning() {
        requireStatus(RoomStatus.REVIEWING);
        this.status = RoomStatus.PLANNING;
    }

    // 상태 전이는 방 잠금 안에서 상태를 확인한 뒤에만 부르므로, 여기서 어긋나면 코드 버그다
    private void requireStatus(RoomStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "방 상태가 " + expected + "가 아니라 " + status + "입니다: roomId=" + getId());
        }
    }
}
