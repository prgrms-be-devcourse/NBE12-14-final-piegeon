package world.chakchak.domain.place.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import world.chakchak.domain.comment.dto.CommentListResponse;
import world.chakchak.domain.comment.dto.CommentRequest;
import world.chakchak.domain.comment.dto.CommentResponse;
import world.chakchak.domain.place.dto.PlaceBoardResponse;
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

    /** P-03 장소 칸반 보드 */
    @Operation(
            summary = "장소 칸반 보드 (P-03)",
            description =
                    "방의 장소 후보를 칸과 함께 돌려준다. "
                            + "칸(확정 -> 전체 -> 부분 -> 여집합) 순서이고, 같은 칸은 가고 싶어요가 많은 순이다.")
    @GetMapping
    public ResponseEntity<ApiResponse<PlaceBoardResponse>> getBoard(
            @PathVariable Long roomId, @AuthenticationPrincipal LoginUser loginUser) {
        PlaceBoardResponse response = placeCandidateService.getBoard(roomId, loginUser.userId());
        return ResponseEntity.ok(ApiResponse.success("PLACE_CANDIDATES_FETCHED", response));
    }

    /** P-08 장소 코멘트 목록 */
    @Operation(summary = "장소 코멘트 목록 (P-08)", description = "장소 후보에 달린 코멘트를 오래된 순으로 돌려준다.")
    @GetMapping("/{placeId}/comments")
    public ResponseEntity<ApiResponse<CommentListResponse>> getComments(
            @PathVariable Long roomId,
            @PathVariable Long placeId,
            @AuthenticationPrincipal LoginUser loginUser) {
        CommentListResponse response =
                placeCandidateService.getComments(roomId, placeId, loginUser.userId());
        return ResponseEntity.ok(ApiResponse.success("COMMENTS_FETCHED", response));
    }

    /** P-09 장소 코멘트 작성 */
    @Operation(
            summary = "장소 코멘트 작성 (P-09)",
            description = "장소 후보에 코멘트를 남긴다. 확정된 장소에도 남길 수 있고, 방이 확정·만료되면 남길 수 없다.")
    @PostMapping("/{placeId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> addComment(
            @PathVariable Long roomId,
            @PathVariable Long placeId,
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody CommentRequest request) {
        CommentResponse response =
                placeCandidateService.addComment(roomId, placeId, loginUser.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("COMMENT_CREATED", "코멘트를 남겼습니다.", response));
    }
}
