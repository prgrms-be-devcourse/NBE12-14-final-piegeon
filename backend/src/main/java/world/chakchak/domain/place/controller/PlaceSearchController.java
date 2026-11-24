package world.chakchak.domain.place.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import world.chakchak.domain.place.dto.PlaceSearchResponse;
import world.chakchak.domain.place.service.PlaceSearchService;
import world.chakchak.global.ratelimit.RateLimit;
import world.chakchak.global.ratelimit.RateLimitKey;
import world.chakchak.global.response.ApiResponse;
import world.chakchak.global.security.LoginUser;

@Tag(name = "장소 검색", description = "Google 장소 검색 (P-01)")
@RestController
@RequestMapping("/api/rooms/{roomId}/places")
@RequiredArgsConstructor
public class PlaceSearchController {

    private final PlaceSearchService placeSearchService;

    /** P-01 장소 검색 */
    @Operation(
            summary = "장소 검색 (P-01)",
            description =
                    "검색어 앞에 여행지를 붙여 Google에서 찾는다. 이 방에 이미 올라온 장소는 "
                            + "alreadyRegistered가 true다. 다음 페이지는 nextPageToken을 pageToken으로 보낸다.")
    @RateLimit(key = RateLimitKey.USER, limit = 30, perSeconds = 60)
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PlaceSearchResponse>> search(
            @PathVariable Long roomId,
            @AuthenticationPrincipal LoginUser loginUser,
            @RequestParam @Size(min = 2, max = 50) String query,
            @RequestParam(required = false) String pageToken) {
        PlaceSearchResponse response =
                placeSearchService.search(roomId, loginUser.userId(), query, pageToken);
        return ResponseEntity.ok(ApiResponse.success("PLACES_SEARCHED", response));
    }
}
