package world.chakchak.domain.room.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import world.chakchak.global.error.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum RoomErrorCode implements ErrorCode {
    DEPARTURE_TOO_SOON(HttpStatus.BAD_REQUEST, "출발일은 현재 시각으로부터 24시간을 초과해 선택해 주세요."),
    DEPARTURE_TOO_FAR(HttpStatus.BAD_REQUEST, "출발일은 1년 이내로 선택해 주세요."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "여행 기간을 확인해 주세요."),
    // 없는 방
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다."),
    // 이 방의 멤버가 아님
    IS_NOT_MEMBER(HttpStatus.FORBIDDEN, "이 방의 멤버가 아닙니다."),
    // 이 방에서 나간 멤버
    LEFT_MEMBER(HttpStatus.FORBIDDEN, "이 방에서 나간 멤버입니다.");

    private final HttpStatus status;
    private final String message;
}
