package world.chakchak.global.error;

import lombok.Getter;

/** 업무 규칙 위반을 알리는 예외. service와 엔티티는 이 예외만 던지고, 응답으로 바꾸는 일은 GlobalExceptionHandler가 맡는다. */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 에러 응답의 data에 함께 보낼 값. 없으면 null이고, 그러면 응답에서 data가 빠진다. */
    private final Object data;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    /** 프론트가 처리에 쓸 값을 함께 보내는 에러. 예: PLACE_ALREADY_REGISTERED의 placeId */
    public BusinessException(ErrorCode errorCode, Object data) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.data = data;
    }
}
