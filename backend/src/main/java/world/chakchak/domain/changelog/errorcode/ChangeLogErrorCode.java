package world.chakchak.domain.changelog.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import world.chakchak.global.error.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum ChangeLogErrorCode implements ErrorCode {
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "방을 찾을 수 없습니다."),
    ACTOR_NOT_IN_ROOM(HttpStatus.BAD_REQUEST, "행위자는 해당 방의 멤버여야 합니다.");

    private final HttpStatus status;
    private final String message;
}
