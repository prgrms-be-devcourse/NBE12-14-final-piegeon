package world.chakchak.domain.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateRoomRequest(
        @NotBlank(message = "여행방 이름은 필수입니다.") @Size(max = 50, message = "여행방 이름은 50자 이하여야 합니다.")
                String name,
        @NotBlank(message = "여행지는 필수입니다.") @Size(max = 50, message = "여행지는 50자 이하여야 합니다.")
                String destination,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate) {

    public CreateRoomRequest {
        name = trim(name);
        destination = trim(destination);
    }

    private static String trim(String value) {
        return value == null ? null : value.strip();
    }
}
