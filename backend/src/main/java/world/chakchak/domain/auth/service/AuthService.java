package world.chakchak.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import world.chakchak.domain.auth.errorcode.AuthErrorCode;
import world.chakchak.domain.user.dto.UserResponse;
import world.chakchak.domain.user.service.UserService;
import world.chakchak.global.error.BusinessException;

/** 가입과 로그인 검증. 세션을 다루지 않는다. 세션 저장은 controller가 SessionAuthenticator로 한다. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public UserResponse signup(String email, String name, String rawPassword) {
        return userService.register(email, name, passwordEncoder.encode(rawPassword));
    }

    public UserResponse login(String email, String rawPassword) {
        try {
            Authentication authentication =
                    authenticationManager.authenticate(
                            UsernamePasswordAuthenticationToken.unauthenticated(
                                    email, rawPassword));
            AuthUserDetails user = (AuthUserDetails) authentication.getPrincipal();
            return new UserResponse(
                    user.getUserId(), user.getEmail(), user.getName(), user.getCreatedAt());
        } catch (BadCredentialsException e) {
            // 이메일이 없는 경우와 비밀번호가 틀린 경우 모두 이 예외로 온다. 둘을 구분해서 알려주지 않는다.
            // DB 장애 같은 다른 인증 예외는 잡지 않는다. 잡으면 장애가 "비밀번호 틀림"으로 보이고 error 로그가 남지 않는다.
            throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
        }
    }
}
