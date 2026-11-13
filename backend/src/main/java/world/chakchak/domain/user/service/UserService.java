package world.chakchak.domain.user.service;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.user.UserErrorCode;
import world.chakchak.domain.user.dto.UserCredentials;
import world.chakchak.domain.user.dto.UserResponse;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;
import world.chakchak.global.error.AuthErrorCode;
import world.chakchak.global.error.BusinessException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    /** passwordHash는 이미 해시된 값이어야 한다. */
    @Transactional
    public UserResponse register(String email, String name, String passwordHash) {
        if (userRepository.existsByEmail(User.normalizeEmail(email))) {
            throw new BusinessException(UserErrorCode.USER_EMAIL_DUPLICATED);
        }
        try {
            User user = userRepository.saveAndFlush(User.create(email, name, passwordHash));
            return UserResponse.from(user);
        } catch (DataIntegrityViolationException e) {
            // 같은 이메일의 가입 요청이 동시에 들어와 위의 확인을 둘 다 통과한 경우. DB의 uk_users_email에 걸린다.
            throw new BusinessException(UserErrorCode.USER_EMAIL_DUPLICATED);
        }
    }

    /** 로그인한 본인 조회. 세션은 남아 있는데 사용자가 없으면(탈퇴 등) 다시 로그인하게 401로 응답한다. */
    public UserResponse getMe(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.AUTH_UNAUTHENTICATED));
    }

    public UserResponse getById(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    public Optional<UserCredentials> findCredentialsByEmail(String email) {
        return userRepository.findByEmail(User.normalizeEmail(email))
                .map(UserCredentials::from);
    }
}
