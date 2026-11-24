package world.chakchak.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bucket4j.TimeMeter;
import java.lang.annotation.Annotation;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 토큰 버킷 규칙. Spring 없이 시간을 직접 돌려 확인한다. */
class RateLimiterTest {

    // 분당 3번
    private static final RateLimit THREE_PER_MINUTE = rule(3, 60);

    private final ManualTime time = new ManualTime();
    private final RateLimiter rateLimiter = new RateLimiter(time);

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("기간 안에서는 한도까지 통과한다")
            void allowsUpToLimit() {
                for (int i = 0; i < 3; i++) {
                    assertThat(rateLimiter.tryConsume("k", THREE_PER_MINUTE)).isZero();
                }
            }

            @Test
            @DisplayName("시간이 지나 토큰이 다시 차면 다시 통과한다")
            void allowsAgainAfterRefill() {
                exhaust("k");

                // 분당 3개 = 20초마다 1개씩 찬다
                time.advance(Duration.ofSeconds(20));

                assertThat(rateLimiter.tryConsume("k", THREE_PER_MINUTE)).isZero();
            }

            @Test
            @DisplayName("키가 다르면 횟수를 따로 센다")
            void countsEachKeySeparately() {
                exhaust("user-1");

                assertThat(rateLimiter.tryConsume("user-2", THREE_PER_MINUTE)).isZero();
            }

            @Test
            @DisplayName("clear하면 모든 키의 횟수가 처음으로 돌아간다")
            void clearResetsAllKeys() {
                exhaust("k");

                rateLimiter.clear();

                assertThat(rateLimiter.tryConsume("k", THREE_PER_MINUTE)).isZero();
            }
        }

        @Nested
        class Failure {

            @Test
            @DisplayName("한도를 넘으면 거절하고, 토큰 1개가 찰 때까지 남은 초를 돌려준다")
            void rejectsOverLimitWithRetryAfter() {
                exhaust("k");

                assertThat(rateLimiter.tryConsume("k", THREE_PER_MINUTE)).isEqualTo(20);
            }

            @Test
            @DisplayName("1초 미만이 남아도 다시 시도할 시각은 1초 뒤로 알려 준다")
            void roundsUpRetryAfter() {
                exhaust("k");
                time.advance(Duration.ofMillis(19_500));

                assertThat(rateLimiter.tryConsume("k", THREE_PER_MINUTE)).isEqualTo(1);
            }
        }
    }

    private void exhaust(String key) {
        for (int i = 0; i < 3; i++) {
            rateLimiter.tryConsume(key, THREE_PER_MINUTE);
        }
    }

    private static RateLimit rule(int limit, long perSeconds) {
        return new RateLimit() {
            @Override
            public RateLimitKey key() {
                return RateLimitKey.IP;
            }

            @Override
            public int limit() {
                return limit;
            }

            @Override
            public long perSeconds() {
                return perSeconds;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return RateLimit.class;
            }
        };
    }

    /** 테스트가 직접 돌리는 시계 */
    private static class ManualTime implements TimeMeter {
        private long nanos;

        void advance(Duration duration) {
            nanos += duration.toNanos();
        }

        @Override
        public long currentTimeNanos() {
            return nanos;
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }
    }
}
