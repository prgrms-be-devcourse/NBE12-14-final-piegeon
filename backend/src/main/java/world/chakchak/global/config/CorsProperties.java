package world.chakchak.global.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application-*.yml의 app.cors 설정. 쿠키를 주고받을 수 있는 프론트 주소 목록이다.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
