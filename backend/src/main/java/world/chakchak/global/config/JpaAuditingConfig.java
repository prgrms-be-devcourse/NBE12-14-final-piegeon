package world.chakchak.global.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * BaseEntity의 createdAt, modifiedAt을 자동으로 채운다.
 *
 * <p>시각은 ClockConfig의 Clock(Asia/Seoul)에서 가져온다. 서버의 시간대가 달라도 한국 시각이 들어간다.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

  @Bean
  public DateTimeProvider auditingDateTimeProvider(Clock clock) {
    return () -> Optional.of(LocalDateTime.now(clock));
  }
}
