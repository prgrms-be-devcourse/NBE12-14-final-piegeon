package world.chakchak.infra.google;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Google Places를 부를 RestClient를 만든다.
 *
 * <p>타임아웃을 거는 이유: Google이 느려지면 우리 서버의 요청 스레드도 같이 묶인다. 명세(P-01)대로 3초가 지나면 끊고 PLACES_API_ERROR(502)로
 * 응답한다.
 */
@Configuration
@EnableConfigurationProperties(GooglePlacesProperties.class)
public class GooglePlacesConfig {

    @Bean
    RestClient googlePlacesRestClient(
            RestClient.Builder builder, GooglePlacesProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());

        return builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
    }
}
