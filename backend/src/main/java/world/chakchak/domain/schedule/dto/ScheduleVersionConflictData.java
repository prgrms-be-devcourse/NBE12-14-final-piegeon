package world.chakchak.domain.schedule.dto;

/** SCHEDULE_VERSION_CONFLICT 응답의 data. 프론트는 이걸 보고 보드를 다시 불러온다. */
public record ScheduleVersionConflictData(Long dayId, long currentVersion) {}
