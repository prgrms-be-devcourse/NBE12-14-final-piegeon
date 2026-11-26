package world.chakchak.domain.room.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import world.chakchak.global.error.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum RoomErrorCode implements ErrorCode {
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다."),
    ROOM_EXPIRED(HttpStatus.GONE, "만료된 방에는 합류할 수 없습니다."),
    INVITE_CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "초대 코드를 찾을 수 없습니다."),
    ALREADY_MEMBER(HttpStatus.CONFLICT, "이미 활동 중인 방 멤버입니다."),
    REJOIN_NOT_ALLOWED(HttpStatus.CONFLICT, "탈퇴한 방에는 다시 합류할 수 없습니다."),
    DEPARTURE_TOO_SOON(HttpStatus.BAD_REQUEST, "출발일은 현재 시각으로부터 24시간을 초과해 선택해 주세요."),
    DEPARTURE_TOO_FAR(HttpStatus.BAD_REQUEST, "출발일은 1년 이내로 선택해 주세요."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "여행 기간을 확인해 주세요.");

    private final HttpStatus status;
    private final String message;
}
