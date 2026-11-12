package world.chakchak.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import world.chakchak.domain.user.service.UserService;

/**
 * Spring Security가 로그인을 검증할 때 이메일로 사용자를 찾는 통로.
 * AuthenticationManager.authenticate()가 이 클래스를 호출한 뒤 비밀번호 해시를 비교한다.
 */
@Service
@RequiredArgsConstructor
public class AuthUserDetailsService implements UserDetailsService {

    private final UserService userService;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userService.findCredentialsByEmail(email)
                .map(credentials -> new AuthUserDetails(
                        credentials.userId(), credentials.email(), credentials.name(), credentials.passwordHash()))
                .orElseThrow(() -> new UsernameNotFoundException("등록되지 않은 이메일입니다."));
    }
}
