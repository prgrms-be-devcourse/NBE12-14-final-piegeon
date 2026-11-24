package world.chakchak.domain.agenda.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import world.chakchak.domain.agenda.dto.AgendaDetailResponse;
import world.chakchak.domain.agenda.service.AgendaService;
import world.chakchak.global.response.ApiResponse;
import world.chakchak.global.security.LoginUser;

/** 최종 확정 투표 (C-01). 경로가 안건 목록(/agendas) 밑이 아니라 AgendaController와 나눴다. */
@RestController
@RequestMapping("/api/rooms/{roomId}")
@RequiredArgsConstructor
@Tag(name = "최종 확정", description = "여행 최종 확정 투표 API")
public class FinalReviewController {

    private final AgendaService agendaService;

    /** C-01 최종 확정 투표 열기 */
    @Operation(
            summary = "최종 확정 투표 열기",
            description = "해당 여행방의 최종 확정 투표를 연다. " + "투표가 열리면 안건 상세 정보를 반환한다.")
    @PostMapping("/final-review")
    public ResponseEntity<ApiResponse<AgendaDetailResponse>> openFinalReview(
            @PathVariable Long roomId, @AuthenticationPrincipal LoginUser loginUser) {
        AgendaDetailResponse response = agendaService.openFinalReview(roomId, loginUser.userId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("FINAL_REVIEW_OPENED", "최종 확정 투표를 열었습니다.", response));
    }
}
