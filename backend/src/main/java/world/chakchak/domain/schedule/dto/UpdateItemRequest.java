package world.chakchak.domain.schedule.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

/** 블록 수정 요청. 시간과 메모는 바꾸지 않는 값도 지금 값 그대로 보낸다. null이면 비운다. */
public record UpdateItemRequest(
        // 예: "15:00". null이면 시간 미정
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @Size(max = 200) String memo,
        // 보드에서 받은 그날의 version 그대로
        @NotNull Long dayVersion) {}
