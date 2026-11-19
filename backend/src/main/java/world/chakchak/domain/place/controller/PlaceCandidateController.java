package world.chakchak.domain.place.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import world.chakchak.domain.place.dto.PlaceCandidateItem;
import world.chakchak.domain.place.dto.RegisterPlaceRequest;
import world.chakchak.domain.place.service.PlaceCandidateService;
import world.chakchak.global.response.ApiResponse;
import world.chakchak.global.security.LoginUser;

@Tag(name = "장소 후보", description = "장소 후보 등록·칸반 보드 (P-01~P-11)")
@RestController
@RequestMapping("/api/rooms/{roomId}/place-candidates")
@RequiredArgsConstructor
public class PlaceCandidateController {

    private final PlaceCandidateService placeCandidateService;

    /** P-02 장소 후보 등록 */
    @Operation(
            summary = "장소 후보 등록 (P-02)",
            description =
                    "Google 장소 ID로 후보를 올린다. 이름, 주소, 종류는 서버가 Google에서 다시 조회한다. "
                            + "올린 사람의 '가고 싶어요'가 함께 저장된다.")
    @PostMapping
    public ResponseEntity<ApiResponse<PlaceCandidateItem>> register(
            @PathVariable Long roomId,
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody RegisterPlaceRequest request) {
        PlaceCandidateItem response =
                placeCandidateService.register(roomId, loginUser.userId(), request.googlePlaceId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("PLACE_CANDIDATE_CREATED", "장소 후보를 올렸습니다.", response));
    }
}
