package world.chakchak.global.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;
import world.chakchak.global.config.ClockConfig;

@IntegrationTest
class BaseEntityAuditingTest {

  @Autowired private UserRepository userRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("생성·수정 시각은 서비스 시간대(Asia/Seoul)의 시각 그대로 DB에 저장된다")
  void storesServiceZoneLocalDateTimeAsIs() {
    LocalDateTime nowInSeoul = LocalDateTime.now(ClockConfig.SERVICE_ZONE);

    User user =
        userRepository.saveAndFlush(
            User.create("audit-" + UUID.randomUUID() + "@test.com", "테스터", "hashed-password"));

    assertThat(user.getCreatedAt()).isCloseTo(nowInSeoul, within(1, ChronoUnit.MINUTES));
    assertThat(user.getModifiedAt()).isCloseTo(nowInSeoul, within(1, ChronoUnit.MINUTES));

    // JVM 시간대가 달라도(로컬 KST, CI UTC) DB에 적힌 글자가 같은 시각이어야 한다.
    String stored =
        jdbcTemplate.queryForObject(
            "SELECT DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') FROM users WHERE id = ?",
            String.class, user.getId());
    assertThat(stored)
        .isEqualTo(user.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
  }
}
