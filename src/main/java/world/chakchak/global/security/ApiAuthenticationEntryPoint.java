package world.chakchak.global.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 로그인 없이 보호된 API를 호출했을 때 실행된다.
 *
 * <p>이 시점은 controller에 도달하기 전인 필터 단계라서 GlobalExceptionHandler가 자동으로 잡지 못한다.
 * 예외를 HandlerExceptionResolver에 넘겨 GlobalExceptionHandler가 같은 형식의 에러 JSON을 만들게 한다.
 */
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final HandlerExceptionResolver handlerExceptionResolver;

    public ApiAuthenticationEntryPoint(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        if (handlerExceptionResolver.resolveException(request, response, null, authException) == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}
