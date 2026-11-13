package world.chakchak;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 테스트용 MySQL 컨테이너. DB가 필요한 테스트는 @Import(TestcontainersConfig.class)를 붙인다. @ServiceConnection이 컨테이너의
 * 접속 주소를 DataSource에 자동으로 연결한다. 실행하려면 Docker가 켜져 있어야 한다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

  @Bean
  @ServiceConnection
  MySQLContainer mysqlContainer() {
    return new MySQLContainer("mysql:8.4");
  }
}
