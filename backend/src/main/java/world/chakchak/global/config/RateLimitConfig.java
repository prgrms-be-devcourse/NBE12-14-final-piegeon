package world.chakchak.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import world.chakchak.global.ratelimit.RateLimitInterceptor;

/**
 * 호출 횟수 제한 인터셉터를 등록한다. app.rate-limit.enabled가 false면(test 프로필) 등록하지 않는다.
 *
 * <p>모든 경로에 걸지만, 실제로 세는 것은 {@code @RateLimit}이 붙은 메서드뿐이다.
 *
 * <p>이 클래스는 제한이 꺼지면 통째로 등록되지 않는다. 다른 MVC 설정(인자 처리, 포맷터 등)을 여기에 넣으면 테스트에서 조용히 빠지므로 넣지 않는다.
 */
@Configuration
@ConditionalOnBooleanProperty(name = "app.rate-limit.enabled", matchIfMissing = true)
@RequiredArgsConstructor
public class RateLimitConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor);
    }
}
