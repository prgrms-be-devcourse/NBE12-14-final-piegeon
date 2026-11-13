package world.chakchak.global.security;

import java.io.Serializable;

/**
 * 세션에 저장되는 로그인 사용자.
 *
 * <p>세션은 Java 직렬화로 DB에 저장된다. 필드를 바꾸면 이미 저장된 세션을 읽지 못하므로,
 * 바뀔 수 있는 값(이름 등)은 넣지 않고 userId로 DB에서 조회한다.
 */
public record LoginUser(Long userId, String email) implements Serializable {
}
