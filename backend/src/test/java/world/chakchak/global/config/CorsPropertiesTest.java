package world.chakchak.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** 설정값 검증은 설정 바인딩만 하는 작은 컨텍스트로 확인한다. DB와 웹 서버는 띄우지 않는다. */
class CorsPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

  @Test
  @DisplayName("allowed-origins에 쉼표로 구분한 주소를 적으면 목록으로 읽는다")
  void bindsOrigins() {
    contextRunner
        .withPropertyValues("app.cors.allowed-origins=http://localhost:3000,https://chakchak.world")
        .run(context ->
            assertThat(context.getBean(CorsProperties.class).allowedOrigins())
                .containsExactly("http://localhost:3000", "https://chakchak.world"));
  }

  @Test
  @DisplayName("allowed-origins가 없으면 서버가 뜨지 않는다")
  void failsWithoutOrigins() {
    contextRunner.run(context -> assertThat(context).hasFailed());
  }

  @Test
  @DisplayName("환경변수가 비어 placeholder가 그대로 남으면 서버가 뜨지 않는다")
  void failsWithUnresolvedPlaceholder() {
    contextRunner
        .withPropertyValues("app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS_NOT_SET}")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  @DisplayName("주소 뒤에 경로가 붙어 있으면 서버가 뜨지 않는다")
  void failsWithPath() {
    contextRunner
        .withPropertyValues("app.cors.allowed-origins=http://localhost:3000/app")
        .run(context -> assertThat(context).hasFailed());
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(CorsProperties.class)
  static class TestConfig {}
}
