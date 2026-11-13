package world.chakchak.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank(message = "이메일을 입력해 주세요.") String email,

    // 가입할 때의 상한과 같다. bcrypt가 처리하지 못하는 긴 입력을 미리 거른다.
    @NotBlank(message = "비밀번호를 입력해 주세요.") @Size(max = 64, message = "비밀번호는 64자 이하로 입력해 주세요.")
        String password) {}
