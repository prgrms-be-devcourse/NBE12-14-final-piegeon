package world.chakchak.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 아닙니다.")
        @Size(max = 255, message = "이메일은 255자 이하로 입력해 주세요.")
        String email,

        @NotBlank(message = "이름을 입력해 주세요.")
        @Size(max = 30, message = "이름은 30자 이하로 입력해 주세요.")
        String name,

        // bcrypt는 앞의 72바이트까지만 쓴다. 한 글자가 1바이트인 문자만 허용해 64자가 72바이트를 넘지 않게 한다.
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하로 입력해 주세요.")
        @Pattern(regexp = "^[\\x21-\\x7E]*$", message = "비밀번호는 영문, 숫자, 특수문자만 쓸 수 있습니다.")
        String password) {
}
