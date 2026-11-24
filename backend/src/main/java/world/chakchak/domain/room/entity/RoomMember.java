package world.chakchak.domain.room.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import world.chakchak.domain.user.entity.User;
import world.chakchak.global.entity.BaseEntity;

@Entity
@Table(name = "plan_room_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    private RoomMember(Room room, User user, Clock clock) {
        LocalDateTime joinedAt = LocalDateTime.now(clock);
        this.room = room;
        this.user = user;
        this.joinedAt = joinedAt;
        this.lastSeenAt = joinedAt;
    }

    public static RoomMember create(Room room, User user, Clock clock) {
        return new RoomMember(room, user, clock);
    }

    public void leave(LocalDateTime leftAt) {
        if (this.leftAt != null) {
            throw new IllegalStateException("이미 퇴장한 멤버입니다: memberId=" + getId());
        }
        this.leftAt = leftAt;
    }
}
