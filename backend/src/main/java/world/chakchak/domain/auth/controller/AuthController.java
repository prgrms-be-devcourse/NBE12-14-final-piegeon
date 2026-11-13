package world.chakchak.domain.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import world.chakchak.domain.auth.dto.LoginRequest;
import world.chakchak.domain.auth.dto.SignupRequest;
import world.chakchak.domain.auth.service.AuthService;
import world.chakchak.domain.user.dto.UserResponse;
import world.chakchak.global.security.LoginUser;
import world.chakchak.global.security.SessionAuthenticator;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final SessionAuthenticator sessionAuthenticator;

  /** 가입하면 바로 로그인 상태가 된다. */
  @PostMapping("/signup")
  public ResponseEntity<UserResponse> signup(
      @Valid @RequestBody SignupRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    UserResponse user = authService.signup(request.email(), request.name(), request.password());
    sessionAuthenticator.login(new LoginUser(user.id(), user.email()), httpRequest, httpResponse);
    return ResponseEntity.status(HttpStatus.CREATED).body(user);
  }

  @PostMapping("/login")
  public ResponseEntity<UserResponse> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    UserResponse user = authService.login(request.email(), request.password());
    sessionAuthenticator.login(new LoginUser(user.id(), user.email()), httpRequest, httpResponse);
    return ResponseEntity.ok(user);
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    sessionAuthenticator.logout(httpRequest, httpResponse);
    return ResponseEntity.noContent().build();
  }
}
