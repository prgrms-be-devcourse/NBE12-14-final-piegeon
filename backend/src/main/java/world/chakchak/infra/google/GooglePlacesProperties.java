package world.chakchak.infra.google;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application-*.yml의 app.google-places 설정.
 *
 * <p>API 키는 환경변수 GOOGLE_PLACES_API_KEY로만 받는다. 값이 비어 있으면 서버가 뜨지 않는다. 키를 빠뜨린 채 배포되어 장소 검색만 조용히 실패하는
 * 일을 막기 위해서다. (CorsProperties와 같은 방식)
 */
@Validated
@ConfigurationProperties(prefix = "app.google-places")
public record GooglePlacesProperties(
        @NotBlank(message = "app.google-places.api-key를 설정해 주세요.") String apiKey,
        @NotBlank(message = "app.google-places.base-url을 설정해 주세요.") String baseUrl,
        @NotNull(message = "app.google-places.timeout을 설정해 주세요.") Duration timeout) {}
