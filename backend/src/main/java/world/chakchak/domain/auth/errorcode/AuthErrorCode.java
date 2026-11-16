package world.chakchak.domain.auth.errorcode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import world.chakchak.global.error.ErrorCode;

/** 인증 도메인의 에러 코드. 로그인 안 됨(UNAUTHORIZED)과 권한 없음(FORBIDDEN)은 모든 API에 공통이라 CommonErrorCode에 있다. */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");

    private final HttpStatus status;
    private final String message;
}
