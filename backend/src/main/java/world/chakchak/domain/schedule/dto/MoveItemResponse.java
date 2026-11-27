package world.chakchak.domain.schedule.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 블록 이동 결과. (B-03)
 *
 * <p>day는 블록이 지금 놓인 날, fromDay는 블록이 떠난 날이다. 같은 날 안에서 옮겼으면 fromDay는 null이고 응답에서 칸이 빠진다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MoveItemResponse(DaySnapshot day, DaySnapshot fromDay) {}
