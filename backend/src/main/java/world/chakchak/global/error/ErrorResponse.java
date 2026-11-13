package world.chakchak.global.error;

import java.util.List;

/**
 * 모든 실패 응답의 본문. fieldErrors는 없을 때도 빈 배열로 보낸다.
 */
public record ErrorResponse(String code, String message, List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> fieldErrors) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), fieldErrors);
    }
}
