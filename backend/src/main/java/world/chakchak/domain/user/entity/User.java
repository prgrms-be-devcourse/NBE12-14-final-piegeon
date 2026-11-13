package world.chakchak.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import world.chakchak.global.entity.BaseEntity;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

  @Column(nullable = false, length = 255)
  private String email;

  @Column(nullable = false, length = 30)
  private String name;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  private User(String email, String name, String passwordHash) {
    this.email = email;
    this.name = name;
    this.passwordHash = passwordHash;
  }

  /** passwordHash는 이미 해시된 값이어야 한다. 평문 비밀번호를 넘기지 않는다. */
  public static User create(String email, String name, String passwordHash) {
    return new User(normalizeEmail(email), name.strip(), passwordHash);
  }

  /** 이메일은 앞뒤 공백을 지우고 소문자로 바꿔서 저장하고 조회한다. */
  public static String normalizeEmail(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }
}
