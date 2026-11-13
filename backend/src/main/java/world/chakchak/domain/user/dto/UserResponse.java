package world.chakchak.domain.user.dto;

import world.chakchak.domain.user.entity.User;

public record UserResponse(Long id, String email, String name) {

  public static UserResponse from(User user) {
    return new UserResponse(user.getId(), user.getEmail(), user.getName());
  }
}
