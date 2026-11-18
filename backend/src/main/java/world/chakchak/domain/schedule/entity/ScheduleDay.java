package world.chakchak.domain.schedule.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.global.entity.BaseEntity;

/**
 * 여행방의 하루. (테이블 schedule_day, V14)
 *
 * <p>방을 만들 때 여행 일수만큼 생긴다. 날짜는 저장하지 않고 "시작일 + (dayIndex - 1)"로 계산한다. version은 이 날의 블록이 바뀔 때마다 1씩
 * 오르고, 요청의 dayVersion과 비교해 동시에 고친 경우를 막는다.
 */
@Entity
@Table(name = "schedule_day")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleDay extends BaseEntity {

    // 이 날이 속한 방
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_room_id", nullable = false)
    private Room room;

    // 1일차면 1. 같은 방에 같은 일차는 하나만 (DB UNIQUE)
    @Column(name = "day_index", nullable = false)
    private int dayIndex;

    // 처음 0, 편집이 성공할 때마다 +1
    @Column(nullable = false)
    private long version;

    private ScheduleDay(Room room, int dayIndex) {
        this.room = room;
        this.dayIndex = dayIndex;
        this.version = 0;
    }

    public static ScheduleDay create(Room room, int dayIndex) {
        return new ScheduleDay(room, dayIndex);
    }

    /** 요청이 본 버전이 지금 버전과 같은지. 다르면 service가 SCHEDULE_VERSION_CONFLICT를 던진다. */
    public boolean isSameVersion(long dayVersion) {
        return this.version == dayVersion;
    }

    /** 이 날의 블록을 추가, 이동, 수정, 삭제한 뒤 부른다. */
    public void increaseVersion() {
        this.version++;
    }

    /** 이 날의 실제 날짜. 1일차가 여행 시작일이다. */
    public LocalDate getDate() {
        return room.getStartDate().plusDays(dayIndex - 1L);
    }
}
