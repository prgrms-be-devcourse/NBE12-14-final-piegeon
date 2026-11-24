package world.chakchak.domain.schedule.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.schedule.entity.ScheduleDay;
import world.chakchak.domain.schedule.entity.ScheduleItem;

/**
 * 일정 보드의 하루. (B-01)
 *
 * <p>블록을 추가·이동·수정·삭제한 뒤에도 바뀐 날을 이 모양으로 돌려준다. 블록은 화면 순서대로 담고, 순서값은 내보내지 않는다.
 */
public record DaySnapshot(
        Long scheduleDayId,
        long dayIndex,
        LocalDate date,
        // 이 날을 고칠 때 요청에 그대로 보내는 값
        long version,
        List<ItemResponse> items) {

    /** 일정 블록 하나 */
    public record ItemResponse(
            Long itemId,
            Long placeId,
            String name,
            String address,
            // 예: "11:00". 시간을 안 정했으면 null
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            String memo) {}

    /**
     * 날과 그 날의 블록으로 만든다.
     *
     * @param items 화면 순서(순서값 오름차순)로 정렬된 블록
     */
    public static DaySnapshot of(ScheduleDay day, List<ScheduleItem> items) {
        return new DaySnapshot(
                day.getId(),
                day.getDayIndex(),
                day.getDate(),
                day.getVersion(),
                items.stream().map(DaySnapshot::toItem).toList());
    }

    private static ItemResponse toItem(ScheduleItem item) {
        PlaceCandidate place = item.getPlaceCandidate();
        return new ItemResponse(
                item.getId(),
                place.getId(),
                place.getName(),
                place.getAddress(),
                item.getStartTime(),
                item.getMemo());
    }
}
