package world.chakchak.domain.user.dto;

import java.time.LocalDateTime;
import world.chakchak.domain.user.entity.User;

public record UserResponse(Long userId, String email, String name, LocalDateTime createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getCreatedAt());
    }
}
