package world.chakchak.domain.schedule.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 블록 이동·수정·삭제 결과. 바뀐 날의 모습(새 version)과 경고.
 *
 * <p>경고가 없으면 응답에서 warnings 칸이 빠진다.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record DayChangeResponse(DaySnapshot day, List<ScheduleWarning> warnings) {}
