package world.chakchak.domain.user.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import world.chakchak.domain.user.entity.User;
import world.chakchak.global.config.ClockConfig;

/** createdAt은 "2026-11-01T10:00:00+09:00"처럼 오프셋을 붙여 내보낸다. 해외에서 접속한 브라우저도 같은 시각으로 해석한다. */
public record UserResponse(Long userId, String email, String name, OffsetDateTime createdAt) {

    public static UserResponse from(User user) {
        return of(user.getId(), user.getEmail(), user.getName(), user.getCreatedAt());
    }

    /** createdAt은 DB에 저장된 서비스 시간대(Asia/Seoul) 기준 시각이다. */
    public static UserResponse of(Long userId, String email, String name, LocalDateTime createdAt) {
        return new UserResponse(
                userId, email, name, createdAt.atZone(ClockConfig.SERVICE_ZONE).toOffsetDateTime());
    }
}
