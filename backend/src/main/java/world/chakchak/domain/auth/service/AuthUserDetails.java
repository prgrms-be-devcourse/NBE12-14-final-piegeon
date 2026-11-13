package world.chakchak.domain.auth.service;

import java.util.Collection;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security가 비밀번호를 비교할 때만 쓰는 사용자 정보. 비밀번호 해시가 들어 있으므로 세션에는 저장하지 않는다. 세션에는 LoginUser를 저장한다.
 */
@Getter
@RequiredArgsConstructor
public class AuthUserDetails implements UserDetails {

  private final Long userId;
  private final String email;
  private final String name;
  private final String passwordHash;

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of();
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return email;
  }
}
