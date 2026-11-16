package world.chakchak.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import world.chakchak.global.error.ErrorCode;

/**
 * 모든 API 응답의 본문. 성공과 실패가 같은 형태를 쓴다.
 *
 * <p>message와 data는 없으면 필드 자체를 응답에서 뺀다. HTTP 상태 코드는 여기에 담지 않고 ResponseEntity로 정한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String code, String message, T data) {

    public static <T> ApiResponse<T> success(String code, String message, T data) {
        return new ApiResponse<>(true, code, message, data);
    }

    /** 보낼 메시지가 없는 성공. */
    public static <T> ApiResponse<T> success(String code, T data) {
        return new ApiResponse<>(true, code, null, data);
    }

    /** 보낼 데이터가 없는 성공. */
    public static ApiResponse<Void> successWithoutData(String code, String message) {
        return new ApiResponse<>(true, code, message, null);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.name(), errorCode.getMessage(), null);
    }

    /** 프론트가 처리에 쓸 값을 함께 보내는 실패. */
    public static <T> ApiResponse<T> error(ErrorCode errorCode, T data) {
        return new ApiResponse<>(false, errorCode.name(), errorCode.getMessage(), data);
    }
}
