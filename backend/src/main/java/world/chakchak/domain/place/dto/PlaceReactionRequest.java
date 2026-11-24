package world.chakchak.domain.place.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import world.chakchak.domain.place.entity.PlaceReactionType;

/** 내 반응 정하기 요청. 가고 싶어요(WANT)나 별로예요(NOPE) 중 하나를 보낸다. (P-04) */
public record PlaceReactionRequest(
        @Schema(description = "누른 버튼. WANT = 가고 싶어요, NOPE = 별로예요", example = "WANT") @NotNull
                PlaceReactionType reaction) {}
