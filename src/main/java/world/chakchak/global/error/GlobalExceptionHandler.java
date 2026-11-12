package world.chakchak.global.error;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
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

/**
 * 예외를 공통 에러 응답(ErrorResponse)으로 바꾸는 한 곳.
 * 4xx는 스택트레이스 없이 warn으로, 5xx는 스택트레이스와 함께 error로 로그를 남긴다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        log.warn("[{}] {}", e.getErrorCode().name(), e.getMessage());
        return toResponse(e.getErrorCode());
    }

    /** 요청 DTO의 검증(@Valid) 실패. 필드별 사유를 함께 보낸다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        ErrorCode code = CommonErrorCode.COMMON_INVALID_INPUT;
        log.warn("[{}] {}", code.name(), fieldErrors);
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code, fieldErrors));
    }

    /** 경로 변수나 쿼리 파라미터의 검증 실패. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleInvalidParameter(HandlerMethodValidationException e) {
        log.warn("[{}] {}", CommonErrorCode.COMMON_INVALID_INPUT.name(), e.getMessage());
        return toResponse(CommonErrorCode.COMMON_INVALID_INPUT);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception e) {
        log.warn("[{}] {}", CommonErrorCode.COMMON_MALFORMED_REQUEST.name(), e.getMessage());
        return toResponse(CommonErrorCode.COMMON_MALFORMED_REQUEST);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException e) {
        return toResponse(CommonErrorCode.COMMON_NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return toResponse(CommonErrorCode.COMMON_METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        return toResponse(CommonErrorCode.COMMON_UNSUPPORTED_MEDIA_TYPE);
    }

    /** 다른 요청이 같은 데이터를 먼저 수정하고 커밋한 경우(@Version 충돌). */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleVersionConflict(ObjectOptimisticLockingFailureException e) {
        log.warn("[{}] {}", CommonErrorCode.COMMON_VERSION_CONFLICT.name(), e.getMessage());
        return toResponse(CommonErrorCode.COMMON_VERSION_CONFLICT);
    }

    /** 동시에 들어온 요청이 DB의 unique 제약 등에 걸린 경우. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("[{}] {}", CommonErrorCode.COMMON_CONFLICT.name(), e.getMessage());
        return toResponse(CommonErrorCode.COMMON_CONFLICT);
    }

    /** 로그인 없이 보호된 API를 호출한 경우. ApiAuthenticationEntryPoint가 이쪽으로 넘긴다. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleUnauthenticated(AuthenticationException e) {
        return toResponse(AuthErrorCode.AUTH_UNAUTHENTICATED);
    }

    /** 권한이 없는 경우. ApiAccessDeniedHandler가 이쪽으로 넘긴다. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(AccessDeniedException e) {
        return toResponse(AuthErrorCode.AUTH_FORBIDDEN);
    }

    /** 위에서 처리하지 못한 예외. 내부 정보가 새지 않도록 고정 문구만 응답한다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("[{}] 처리하지 못한 예외", CommonErrorCode.COMMON_INTERNAL_ERROR.name(), e);
        return toResponse(CommonErrorCode.COMMON_INTERNAL_ERROR);
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode code) {
        return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
    }
}
