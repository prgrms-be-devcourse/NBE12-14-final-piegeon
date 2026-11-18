package world.chakchak.domain.place.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** P-02 장소 후보 등록 요청. 이름·주소·종류는 받지 않고 서버가 Google에서 다시 조회한다. */
public record RegisterPlaceRequest(@NotBlank @Size(max = 255) String googlePlaceId) {}
