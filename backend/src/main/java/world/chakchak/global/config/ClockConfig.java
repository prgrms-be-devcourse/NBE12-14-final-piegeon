package world.chakchak.global.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 현재 시각은 이 Clock을 주입받아 구한다. 테스트에서 Clock.fixed(...)로 바꿔 시각을 고정할 수 있다.
 */
@Configuration
public class ClockConfig {

    /** 마감일처럼 날짜로 계산하는 규칙의 기준 시간대. */
    public static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(SERVICE_ZONE);
    }
}
