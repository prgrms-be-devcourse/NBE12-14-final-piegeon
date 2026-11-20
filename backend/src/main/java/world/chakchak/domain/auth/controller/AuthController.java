package world.chakchak.domain.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import world.chakchak.domain.auth.dto.LoginRequest;
import world.chakchak.domain.auth.dto.SignupRequest;
import world.chakchak.domain.auth.service.AuthService;
import world.chakchak.domain.changelog.service.ChangeLogStreamService;
import world.chakchak.domain.user.dto.UserResponse;
import world.chakchak.global.response.ApiResponse;
import world.chakchak.global.security.LoginUser;
import world.chakchak.global.security.SessionAuthenticator;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SessionAuthenticator sessionAuthenticator;
    private final ChangeLogStreamService changeLogStreamService;

    /** 가입만 한다. 로그인 상태가 되지 않으므로 가입 후 로그인을 따로 해야 한다. */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponse>> signup(
            @Valid @RequestBody SignupRequest request) {
        UserResponse user = authService.signup(request.email(), request.name(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("USER_CREATED", "회원가입이 완료되었습니다.", user));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        UserResponse user = authService.login(request.email(), request.password());
        sessionAuthenticator.login(
                new LoginUser(user.userId(), user.email()), httpRequest, httpResponse);
        return ResponseEntity.ok(ApiResponse.success("LOGIN_SUCCESS", user));
    }

    /** 이미 로그아웃된 상태(세션 만료 포함)에서 호출해도 204로 응답한다. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            @AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser != null) changeLogStreamService.closeConnectionsForUser(loginUser.userId());
        sessionAuthenticator.logout(httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }
}
