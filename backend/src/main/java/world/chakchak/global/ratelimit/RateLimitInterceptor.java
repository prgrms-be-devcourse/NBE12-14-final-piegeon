package world.chakchak.global.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import world.chakchak.global.error.BusinessException;
import world.chakchak.global.error.CommonErrorCode;
import world.chakchak.global.security.LoginUser;

/**
 * controller 메서드에 {@link RateLimit}이 있으면, 메서드가 실행되기 전에 호출 횟수를 확인한다.
 *
 * <p>Security 필터 다음, controller 앞에서 돈다. 그래서 로그인 확인은 이미 끝나 있고, 막힌 요청은 service·DB·외부 API까지 가지 않는다.
 * 넘으면 Retry-After 헤더(초)를 붙이고 BusinessException을 던진다. 응답 본문은 GlobalExceptionHandler가 다른 에러와 같은 형식으로
 * 만든다.
 */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RateLimit rule = method.getMethodAnnotation(RateLimit.class);
        if (rule == null) {
            return true;
        }

        // 메서드마다 따로 센다. 예: "AuthController.login:203.0.113.7", "RoomController.create:42"
        String key =
                method.getBeanType().getSimpleName()
                        + "."
                        + method.getMethod().getName()
                        + ":"
                        + subject(rule.key(), request);
        long retryAfterSeconds = rateLimiter.tryConsume(key, rule);
        if (retryAfterSeconds > 0) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
            throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS);
        }
        return true;
    }

    // 누구로 셀지. prod는 forward-headers-strategy: native라서 Caddy 뒤에서도 getRemoteAddr()가 실제 사용자 IP다
    private String subject(RateLimitKey key, HttpServletRequest request) {
        if (key == RateLimitKey.USER) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof LoginUser user) {
                return "user-" + user.userId();
            }
            // 로그인 API가 아니면 Security가 먼저 401로 막으므로 여기 오지 않는다. 혹시 오면 IP로 센다
        }
        return "ip-" + request.getRemoteAddr();
    }
}
