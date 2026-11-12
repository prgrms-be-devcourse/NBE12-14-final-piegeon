package world.chakchak.global.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 로그인은 했지만 권한이 없는 경로에 접근했을 때 실행된다.
 * ApiAuthenticationEntryPoint와 같은 방식으로 GlobalExceptionHandler에 처리를 넘긴다.
 */
@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final HandlerExceptionResolver handlerExceptionResolver;

    public ApiAccessDeniedHandler(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver) {
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        if (handlerExceptionResolver.resolveException(request, response, null, accessDeniedException) == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
    }
}
