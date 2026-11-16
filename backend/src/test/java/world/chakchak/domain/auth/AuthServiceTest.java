package world.chakchak.domain.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import world.chakchak.domain.auth.errorcode.AuthErrorCode;
import world.chakchak.domain.auth.service.AuthService;
import world.chakchak.domain.user.service.UserService;
import world.chakchak.global.error.BusinessException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserService userService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks private AuthService authService;

    @Test
    @DisplayName("이메일이 없거나 비밀번호가 틀리면 LOGIN_FAILED를 던진다")
    void loginWithBadCredentials() {
        given(authenticationManager.authenticate(any(Authentication.class)))
                .willThrow(new BadCredentialsException("자격 증명에 실패하였습니다."));

        assertThatThrownBy(() -> authService.login("tester@example.com", "password123!"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("로그인 검증 중 DB 장애가 나면 비밀번호 오류로 바꾸지 않고 그대로 던진다")
    void loginWithServiceFailure() {
        given(authenticationManager.authenticate(any(Authentication.class)))
                .willThrow(new InternalAuthenticationServiceException("DB 연결 실패"));

        assertThatThrownBy(() -> authService.login("tester@example.com", "password123!"))
                .isInstanceOf(InternalAuthenticationServiceException.class);
    }
}
