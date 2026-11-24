package world.chakchak.global.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * controller 메서드에 붙여 호출 횟수를 제한한다. {@code perSeconds}초 동안 {@code limit}번까지 받고, 넘으면 429
 * TOO_MANY_REQUESTS로 응답한다.
 *
 * <p>예: {@code @RateLimit(key = RateLimitKey.IP, limit = 20, perSeconds = 60)} = IP마다 분당 20번
 *
 * <p>횟수는 메서드마다 따로 센다. 로그인을 20번 해도 가입 횟수는 줄지 않는다. 검사는 {@link RateLimitInterceptor}가 controller보다 먼저
 * 하므로, 막힌 요청은 service까지 가지 않는다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** 누구 기준으로 셀지 */
    RateLimitKey key();

    /** 기간 안에 받을 수 있는 횟수 */
    int limit();

    /** 기간(초) */
    long perSeconds();
}
