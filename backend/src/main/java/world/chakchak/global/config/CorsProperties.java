package world.chakchak.global.config;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application-*.yml의 app.cors 설정. 쿠키를 주고받을 수 있는 프론트 주소 목록이다.
 *
 * <p>값이 없거나 주소 형식이 아니면 서버가 뜨지 않는다. 환경변수를 빠뜨린 채 배포되어
 * CORS만 조용히 막히는 일을 막기 위해서다.
 */
@Validated
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        @NotEmpty(message = "app.cors.allowed-origins를 설정해 주세요.")
        List<@Pattern(regexp = "^https?://[^/\\s]+$", message = "origin은 http(s)://호스트[:포트] 형식이어야 합니다.") String>
                allowedOrigins) {
}
