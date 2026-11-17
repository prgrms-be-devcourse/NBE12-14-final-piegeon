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
}
