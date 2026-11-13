package world.chakchak.domain.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import world.chakchak.domain.user.dto.UserResponse;
import world.chakchak.domain.user.service.UserService;
import world.chakchak.global.security.LoginUser;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal LoginUser loginUser) {
        return userService.getById(loginUser.userId());
    }
}
