package world.chakchak.global.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 키(예: "AuthController.login:1.2.3.4")마다 토큰 버킷을 하나씩 두고 호출 횟수를 센다.
 *
 * <p>버킷에는 처음에 limit개의 토큰이 있다. 요청마다 1개를 쓰고, 토큰은 perSeconds초에 limit개 속도로 조금씩 다시 찬다. 비어 있으면 거절한다.
 *
 * <p>버킷은 이 서버의 메모리에 둔다. 서버가 1대라 충분하고, 재시작하면 횟수가 처음부터 다시 센다. 서버를 늘리면 Redis 같은 공유 저장소로 바꿔야 한다.
 */
@Component
public class RateLimiter {

    // 가장 긴 기간(시간당 제한)보다 길게 둔다. 그보다 오래 안 온 키는 버킷이 이미 가득 찼으므로 지워도 결과가 같다
    private static final Duration IDLE_EXPIRY = Duration.ofHours(2);
    // 키가 끝없이 늘어 메모리가 차는 것을 막는 상한. 넘으면 오래 안 쓴 것부터 지운다
    private static final long MAX_KEYS = 100_000;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final Cache<String, Bucket> buckets =
            Caffeine.newBuilder().expireAfterAccess(IDLE_EXPIRY).maximumSize(MAX_KEYS).build();

    private final TimeMeter timeMeter;

    @Autowired
    public RateLimiter() {
        this(TimeMeter.SYSTEM_MILLISECONDS);
    }

    /** 테스트에서 시간을 직접 돌리려고 시간 소스를 받는다. */
    RateLimiter(TimeMeter timeMeter) {
        this.timeMeter = timeMeter;
    }

    /**
     * 토큰 1개를 쓴다.
     *
     * @return 통과면 0, 거절이면 다시 시도할 수 있을 때까지 남은 초(1 이상)
     */
    public long tryConsume(String key, RateLimit rule) {
        Bucket bucket = buckets.get(key, k -> newBucket(rule));
        long nanosToWait = bucket.tryConsumeAndReturnRemaining(1).getNanosToWaitForRefill();
        if (nanosToWait == 0) {
            return 0;
        }
        // 초 단위로 올림한다. 0.5초가 남았으면 "0초 뒤"가 아니라 "1초 뒤"
        return (nanosToWait + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND;
    }

    /** 모든 횟수를 처음으로 돌린다. 테스트끼리 횟수가 섞이지 않게 할 때 쓴다. */
    public void clear() {
        buckets.invalidateAll();
    }

    private Bucket newBucket(RateLimit rule) {
        return Bucket.builder()
                .addLimit(limit ->
                        limit.capacity(rule.limit())
                                .refillGreedy(rule.limit(), Duration.ofSeconds(rule.perSeconds())))
                .withCustomTimePrecision(timeMeter)
                .build();
    }
}
