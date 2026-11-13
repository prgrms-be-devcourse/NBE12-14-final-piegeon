package world.chakchak.domain.user.dto;

import world.chakchak.domain.user.entity.User;

/**
 * 로그인 검증에 필요한 값. 비밀번호 해시가 들어 있으므로 API 응답으로 내보내지 않는다.
 */
public record UserCredentials(Long userId, String email, String name, String passwordHash) {

    public static UserCredentials from(User user) {
        return new UserCredentials(user.getId(), user.getEmail(), user.getName(), user.getPasswordHash());
    }
}
