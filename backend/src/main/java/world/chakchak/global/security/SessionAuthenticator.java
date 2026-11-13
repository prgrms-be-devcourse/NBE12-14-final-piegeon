package world.chakchak.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * 인증 정보를 세션에 저장하고(로그인) 세션을 지운다(로그아웃). HttpServletRequest를 다루는 코드를 이 한 곳에 모아서 service가 웹 환경에 묶이지 않게
 * 한다.
 */
@Component
@RequiredArgsConstructor
public class SessionAuthenticator {

  private final SecurityContextRepository securityContextRepository;

  public void login(LoginUser loginUser, HttpServletRequest request, HttpServletResponse response) {
    if (request.getSession(false) != null) {
      // 로그인 전부터 쓰던 세션 ID를 계속 쓰지 않도록 새로 발급한다.
      request.changeSessionId();
    }
    Authentication authentication =
        UsernamePasswordAuthenticationToken.authenticated(loginUser, null, List.of());

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    // 직접 만든 로그인 API에서는 이 호출을 해야 인증 정보가 세션에 저장된다.
    securityContextRepository.saveContext(context, request, response);
  }

  public void logout(HttpServletRequest request, HttpServletResponse response) {
    new SecurityContextLogoutHandler().logout(request, response, null);
  }
}
