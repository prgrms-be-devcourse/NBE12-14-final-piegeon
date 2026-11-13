package world.chakchak.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import world.chakchak.domain.user.entity.User;

/**
 * 엔티티 규칙은 Spring을 띄우지 않고 검증한다.
 */
class UserTest {

    @Test
    @DisplayName("이메일은 앞뒤 공백을 지우고 소문자로 바꿔서 저장한다")
    void normalizesEmail() {
        User user = User.create("  Tester@Example.COM ", " 테스터 ", "hashed-password");

        assertThat(user.getEmail()).isEqualTo("tester@example.com");
        assertThat(user.getName()).isEqualTo("테스터");
    }
}
