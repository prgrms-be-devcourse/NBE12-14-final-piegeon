package world.chakchak.global.security;

import java.io.Serializable;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * 세션에 저장되는 로그인 사용자.
 *
 * <p>세션은 Java 직렬화로 DB에 저장된다. 바뀔 수 있는 값(이름 등)은 넣지 않고 userId로 DB에서 조회한다.
 */
public record LoginUser(Long userId, String email) implements AuthenticatedPrincipal, Serializable {

  /**
   * Spring Session이 세션 테이블의 PRINCIPAL_NAME(VARCHAR(100))에 저장하는 값. 정의하지 않으면 "LoginUser[userId=1,
   * email=...]" 전체가 저장되어 이메일이 길 때 100자를 넘는다. userId로 두면 길이가 일정하고, 특정 사용자의 세션을 userId로 찾아 지울 수 있다.
   */
  @Override
  public String getName() {
    return String.valueOf(userId);
  }
}
