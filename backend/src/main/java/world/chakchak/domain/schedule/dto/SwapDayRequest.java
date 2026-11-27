package world.chakchak.domain.schedule.dto;

import jakarta.validation.constraints.NotNull;

/** 두 날의 일정을 통째로 맞바꾸는 요청. 경로의 날과 otherDayId의 날을 바꾼다. */
public record SwapDayRequest(
        // 보드에서 받은, 경로에 적은 날의 version 그대로
        @NotNull Long dayVersion,
        // 맞바꿀 날
        @NotNull Long otherDayId,
        // 보드에서 받은, 맞바꿀 날의 version 그대로
        @NotNull Long otherDayVersion) {}
