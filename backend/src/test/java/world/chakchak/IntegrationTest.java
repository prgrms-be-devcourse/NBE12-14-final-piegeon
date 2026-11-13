package world.chakchak;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 실제 서버를 임의 포트로 띄우고 Testcontainers의 MySQL에 연결하는 통합 테스트에 붙인다.
 *
 * <p>아래 세 어노테이션을 하나로 묶은 것이다. 테스트마다 따로 적으면 하나를 빠뜨리기 쉽고,
 * 설정이 조금이라도 다르면 Spring이 서버와 DB 컨테이너를 테스트 클래스마다 새로 띄워 느려진다.
 * 이 어노테이션만 쓰면 모든 통합 테스트가 같은 설정을 써서 한 번 띄운 것을 재사용한다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
public @interface IntegrationTest {
}
