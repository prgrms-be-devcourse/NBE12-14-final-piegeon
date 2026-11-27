package world.chakchak.domain.schedule.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import world.chakchak.global.error.ErrorCode;

/**
 * 일정 보드(ScheduleDay, ScheduleItem) 관련 에러 코드.
 *
 * <p>상수 이름이 곧 응답의 code다. 노션 명세의 B-01~B-05 에러 표와 "9-1. 보드 쓰기 공통 규칙"에 적힌 이름을 그대로 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum ScheduleErrorCode implements ErrorCode {
    // 없거나 다른 방의 날 (B-02, B-03에서 다른 날로 옮길 때)
    SCHEDULE_DAY_NOT_FOUND(HttpStatus.NOT_FOUND, "일정의 해당 날짜를 찾을 수 없습니다."),
    // 없거나 다른 방의 블록 (B-03, B-04, B-05)
    SCHEDULE_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "일정 블록을 찾을 수 없습니다."),
    // 방이 PLANNING이 아니라 보드를 고칠 수 없음. 설문 중, 최종 검토·확정 뒤, 만료 모두 (B-02~B-05)
    BOARD_LOCKED(HttpStatus.CONFLICT, "지금 단계에서는 일정을 수정할 수 없습니다."),
    // 요청의 dayVersion이 서버와 다름 (B-02~B-05)
    SCHEDULE_VERSION_CONFLICT(HttpStatus.CONFLICT, "다른 멤버가 먼저 일정을 수정했습니다. 최신 보드를 다시 불러와 주세요."),
    // 하루 블록이 10개를 넘음 (B-02, B-03에서 다른 날로 옮길 때)
    DAY_ITEM_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "일정 블록은 하루에 10개까지 넣을 수 있습니다.");

    private final HttpStatus status;
    private final String message;
}
