package world.chakchak.domain.schedule.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/** 블록 이동 요청. targetDayId를 보내면 그날로 옮기고, 안 보내면 같은 날 안에서 옮긴다. */
public record MoveItemRequest(
        // 이 블록 바로 뒤로 옮긴다. null이면 맨 앞. 블록이 놓일 날의 다른 블록이어야 한다
        Long afterItemId,
        // 보드에서 받은, 블록이 지금 있는 날의 version 그대로
        @NotNull Long dayVersion,
        // 옮겨 갈 날. null이면 같은 날 안에서 옮긴다
        Long targetDayId,
        // 보드에서 받은, 옮겨 갈 날의 version 그대로. targetDayId를 보내면 꼭 보낸다
        Long targetDayVersion) {

    // 요청·응답 JSON의 칸이 아니라 검증용이다. targetDayId만 오고 버전이 없으면 400 INVALID_INPUT
    @JsonIgnore
    @AssertTrue(message = "targetDayId를 보내면 targetDayVersion도 보내야 합니다.")
    public boolean isTargetDayVersionPresent() {
        return targetDayId == null || targetDayVersion != null;
    }
}
