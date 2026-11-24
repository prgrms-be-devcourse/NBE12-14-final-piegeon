package world.chakchak.domain.schedule.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

/** 블록 추가 요청. (B-02) */
public record AddItemRequest(
        // 넣을 장소. 확정된 장소만 된다
        @NotNull Long placeId,
        // 이 블록 바로 뒤에 넣는다. null이면 맨 앞
        Long afterItemId,
        // 예: "14:00". 안 보내면 시간 미정
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @Size(max = 200) String memo,
        // 보드에서 받은 그날의 version 그대로
        @NotNull Long dayVersion) {}
