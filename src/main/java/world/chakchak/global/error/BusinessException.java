package world.chakchak.global.error;

import lombok.Getter;

/**
 * 업무 규칙 위반을 알리는 예외. service와 엔티티는 이 예외만 던지고,
 * 응답으로 바꾸는 일은 GlobalExceptionHandler가 맡는다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
