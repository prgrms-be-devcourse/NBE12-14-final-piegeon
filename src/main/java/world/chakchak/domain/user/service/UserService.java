package world.chakchak.domain.user.service;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.user.UserErrorCode;
import world.chakchak.domain.user.dto.UserCredentials;
import world.chakchak.domain.user.dto.UserResponse;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;
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
        User user = userRepository.save(User.create(email, name, passwordHash));
        return UserResponse.from(user);
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
