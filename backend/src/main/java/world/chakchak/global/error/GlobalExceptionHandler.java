package world.chakchak.global.error;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import world.chakchak.global.response.ApiResponse;

/** 예외를 공통 에러 응답(ErrorResponse)으로 바꾸는 한 곳. 4xx는 스택트레이스 없이 warn으로, 5xx는 스택트레이스와 함께 error로 로그를 남긴다. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        log.warn("[{}] {}", e.getErrorCode().name(), e.getMessage());
        return toResponse(e.getErrorCode());
    }

    /** 요청 DTO의 검증(@Valid) 실패. 필드별 사유를 data에 담아 보낸다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<InvalidInputData>> handleInvalidBody(
            MethodArgumentNotValidException e) {
        List<InvalidInputData.FieldError> fieldErrors =
                e.getBindingResult().getFieldErrors().stream()
                        .map(error ->
                                new InvalidInputData.FieldError(
                                        error.getField(), error.getDefaultMessage()))
                        .toList();
        ErrorCode code = CommonErrorCode.INVALID_INPUT;
        log.warn("[{}] {}", code.name(), fieldErrors);
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code, new InvalidInputData(fieldErrors)));
    }

    /** 경로 변수나 쿼리 파라미터의 검증 실패. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidParameter(
            HandlerMethodValidationException e) {
        log.warn("[{}] {}", CommonErrorCode.INVALID_INPUT.name(), e.getMessage());
        return toResponse(CommonErrorCode.INVALID_INPUT);
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception e) {
        log.warn("[{}] {}", CommonErrorCode.INVALID_INPUT.name(), e.getMessage());
        return toResponse(CommonErrorCode.INVALID_INPUT);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException e) {
        return toResponse(CommonErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException e) {
        return toResponse(CommonErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException e) {
        return toResponse(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    /** 다른 요청이 같은 데이터를 먼저 수정하고 커밋한 경우(@Version 충돌). */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleVersionConflict(
            ObjectOptimisticLockingFailureException e) {
        log.warn("[{}] {}", CommonErrorCode.VERSION_CONFLICT.name(), e.getMessage());
        return toResponse(CommonErrorCode.VERSION_CONFLICT);
    }

    /**
     * 로그인 검증 도중 서버 쪽 문제(DB 장애 등)가 난 경우. AuthenticationException의 하위 타입이지만 사용자 잘못이 아니므로 401이 아니라
     * 500으로 응답하고 error 로그를 남긴다.
     */
    @ExceptionHandler(AuthenticationServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationFailure(
            AuthenticationServiceException e) {
        log.error("[{}] 인증 처리 중 오류", CommonErrorCode.INTERNAL_ERROR.name(), e);
        return toResponse(CommonErrorCode.INTERNAL_ERROR);
    }

    /** 로그인 없이 보호된 API를 호출한 경우. ApiAuthenticationEntryPoint가 이쪽으로 넘긴다. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthenticated(AuthenticationException e) {
        return toResponse(CommonErrorCode.UNAUTHORIZED);
    }

    /** 권한이 없는 경우. ApiAccessDeniedHandler가 이쪽으로 넘긴다. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(AccessDeniedException e) {
        return toResponse(CommonErrorCode.FORBIDDEN);
    }

    /**
     * 위에서 처리하지 못한 예외. 내부 정보가 새지 않도록 고정 문구만 응답한다.
     *
     * <p>Spring이 스스로 4xx로 정한 예외(ResponseStatusException, 필수 경로 변수 누락, 업로드 크기 초과 등)는 클라이언트 잘못이므로 그
     * 상태 코드를 유지한다. 500으로 바꾸면 사용자 실수에 알람이 울린다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse springError
                && springError.getStatusCode().is4xxClientError()) {
            HttpStatusCode status = springError.getStatusCode();
            ErrorCode code = clientErrorCodeOf(status);
            log.warn("[{}] {} {}", code.name(), status.value(), e.getMessage());
            return ResponseEntity.status(status).body(ApiResponse.error(code));
        }
        log.error("[{}] 처리하지 못한 예외", CommonErrorCode.INTERNAL_ERROR.name(), e);
        return toResponse(CommonErrorCode.INTERNAL_ERROR);
    }

    /** 상태 코드에 맞는 공통 에러 코드를 고른다. 따로 정해 둔 코드가 없는 4xx는 INVALID_INPUT로 응답한다. */
    private ErrorCode clientErrorCodeOf(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> CommonErrorCode.NOT_FOUND;
            case 405 -> CommonErrorCode.METHOD_NOT_ALLOWED;
            case 415 -> CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> CommonErrorCode.INVALID_INPUT;
        };
    }

    private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode code) {
        return ResponseEntity.status(code.getStatus()).body(ApiResponse.error(code));
    }
}
