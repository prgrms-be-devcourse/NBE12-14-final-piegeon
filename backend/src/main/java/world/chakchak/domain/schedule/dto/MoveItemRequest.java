package world.chakchak.domain.schedule.dto;

import jakarta.validation.constraints.NotNull;

/** 블록 이동 요청. 같은 날 안에서만 옮긴다. */
public record MoveItemRequest(
        // 이 블록 바로 뒤로 옮긴다. null이면 맨 앞. 같은 날의 다른 블록이어야 한다
        Long afterItemId,
        // 보드에서 받은 그날의 version 그대로
        @NotNull Long dayVersion) {}
