package world.chakchak.domain.schedule.dto;

import java.util.List;
import world.chakchak.domain.room.entity.RoomStatus;

/**
 * 일정 보드 전체. (B-01)
 *
 * <p>방이 PLANNING일 때만 고칠 수 있어서, 프론트는 roomStatus를 보고 편집 버튼을 보여 줄지 정한다.
 */
public record ScheduleBoardResponse(RoomStatus roomStatus, List<DaySnapshot> days) {}
