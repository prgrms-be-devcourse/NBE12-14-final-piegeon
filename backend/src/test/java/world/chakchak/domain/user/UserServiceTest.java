package world.chakchak.domain.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;
import world.chakchak.domain.user.service.UserService;
import world.chakchak.global.error.AuthErrorCode;
import world.chakchak.global.error.BusinessException;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;

  @InjectMocks private UserService userService;

  @Test
  @DisplayName("같은 이메일의 가입이 동시에 들어와 DB unique 제약에 걸리면 USER_EMAIL_DUPLICATED를 던진다")
  void registerConcurrentDuplicate() {
    given(userRepository.existsByEmail("tester@example.com")).willReturn(false);
    given(userRepository.saveAndFlush(any(User.class)))
        .willThrow(new DataIntegrityViolationException("Duplicate entry for key 'uk_users_email'"));

    assertThatThrownBy(() -> userService.register("tester@example.com", "테스터", "hashed-password"))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(UserErrorCode.USER_EMAIL_DUPLICATED);
  }

  @Test
  @DisplayName("세션은 남아 있지만 사용자가 없으면 내 정보 조회는 AUTH_UNAUTHENTICATED를 던진다")
  void getMeWithoutUser() {
    given(userRepository.findById(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> userService.getMe(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(AuthErrorCode.AUTH_UNAUTHENTICATED);
  }
}
