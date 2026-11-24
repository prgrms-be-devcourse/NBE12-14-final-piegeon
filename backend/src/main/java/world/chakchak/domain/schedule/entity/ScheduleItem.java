package world.chakchak.domain.schedule.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.place.errorcode.PlaceErrorCode;
import world.chakchak.global.entity.BaseEntity;
import world.chakchak.global.error.BusinessException;

/**
 * 하루 안에 놓인 일정 블록 하나. (테이블 schedule_item, V15)
 *
 * <p>확정된 장소만 놓을 수 있고, 같은 장소를 여러 블록에 놓아도 된다. 화면 순서는 sortOrder 오름차순이다. sortOrder는 서버가 계산하고 응답에는 내보내지
 * 않는다.
 */
@Entity
@Table(name = "schedule_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleItem extends BaseEntity {

    // 블록이 놓인 날
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_day_id", nullable = false)
    private ScheduleDay scheduleDay;

    // 블록에 놓인 장소 (확정된 후보)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_candidate_id", nullable = false)
    private PlaceCandidate placeCandidate;

    // 순서값. 같은 날에 같은 값은 하나만 (DB UNIQUE)
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    // 선택, null이면 시간 미정
    @Column(name = "start_time")
    private LocalTime startTime;

    // 선택, 200자까지
    @Column(length = 200)
    private String memo;

    private ScheduleItem(
            ScheduleDay scheduleDay,
            PlaceCandidate placeCandidate,
            int sortOrder,
            LocalTime startTime,
            String memo) {
        this.scheduleDay = scheduleDay;
        this.placeCandidate = placeCandidate;
        this.sortOrder = sortOrder;
        this.startTime = startTime;
        this.memo = memo;
    }

    /** 블록을 만든다. (B-02) 확정 안 된 장소면 PLACE_NOT_CONFIRMED. */
    public static ScheduleItem create(
            ScheduleDay scheduleDay,
            PlaceCandidate placeCandidate,
            int sortOrder,
            LocalTime startTime,
            String memo) {
        if (!placeCandidate.isConfirmed()) {
            throw new BusinessException(PlaceErrorCode.PLACE_NOT_CONFIRMED);
        }
        return new ScheduleItem(scheduleDay, placeCandidate, sortOrder, startTime, memo);
    }

    /** 시간과 메모를 바꾼다. (B-04) 둘 다 최종값이고 null이면 비운다. */
    public void update(LocalTime startTime, String memo) {
        this.startTime = startTime;
        this.memo = memo;
    }

    /** 순서값을 바꾼다. (B-03 이동, 순서값 다시 매기기) */
    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
