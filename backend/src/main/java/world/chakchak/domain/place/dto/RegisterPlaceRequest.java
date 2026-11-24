package world.chakchak.domain.place.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** P-02 장소 후보 등록 요청. 이름·주소·종류는 받지 않고 서버가 Google에서 다시 조회한다. */
public record RegisterPlaceRequest(
        @Schema(description = "Google 장소 ID (P-01 검색 결과의 googlePlaceId)", example = "ChIJ...")
                @NotBlank
                @Size(max = 255)
                String googlePlaceId) {}
