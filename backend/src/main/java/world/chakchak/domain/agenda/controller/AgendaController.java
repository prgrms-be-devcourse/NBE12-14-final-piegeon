package world.chakchak.domain.agenda.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import world.chakchak.domain.agenda.dto.AddOptionRequest;
import world.chakchak.domain.agenda.dto.AgendaDetailResponse;
import world.chakchak.domain.agenda.dto.AgendaListResponse;
import world.chakchak.domain.agenda.dto.VoteRequest;
import world.chakchak.domain.agenda.service.AgendaService;
import world.chakchak.domain.comment.dto.CommentListResponse;
import world.chakchak.domain.comment.dto.CommentRequest;
import world.chakchak.domain.comment.dto.CommentResponse;
import world.chakchak.global.response.ApiResponse;
import world.chakchak.global.security.LoginUser;

@RestController
@RequestMapping("/api/rooms/{roomId}/agendas")
@RequiredArgsConstructor
public class AgendaController {

    private final AgendaService agendaService;

    /** V-01 안건 목록 */
    @GetMapping
    public ResponseEntity<ApiResponse<AgendaListResponse>> getAgendas(
            @PathVariable Long roomId, @AuthenticationPrincipal LoginUser loginUser) {
        AgendaListResponse response = agendaService.getAgendas(roomId, loginUser.userId());
        return ResponseEntity.ok(ApiResponse.success("AGENDAS_FETCHED", response));
    }

    /** V-02 안건 상세 */
    @GetMapping("/{agendaId}")
    public ResponseEntity<ApiResponse<AgendaDetailResponse>> getAgenda(
            @PathVariable Long roomId,
            @PathVariable Long agendaId,
            @AuthenticationPrincipal LoginUser loginUser) {
        AgendaDetailResponse response =
                agendaService.getAgenda(roomId, agendaId, loginUser.userId());
        return ResponseEntity.ok(ApiResponse.success("AGENDA_FETCHED", response));
    }

    /** V-03 투표·표 바꾸기 */
    @PutMapping("/{agendaId}/vote")
    public ResponseEntity<ApiResponse<AgendaDetailResponse>> vote(
            @PathVariable Long roomId,
            @PathVariable Long agendaId,
            @Valid @RequestBody VoteRequest request,
            @AuthenticationPrincipal LoginUser loginUser) {
        AgendaDetailResponse response =
                agendaService.vote(roomId, agendaId, loginUser.userId(), request);
        return ResponseEntity.ok(ApiResponse.success("VOTE_SAVED", "투표했습니다.", response));
    }

    /** V-05 안건 코멘트 목록 */
    @GetMapping("/{agendaId}/comments")
    public ResponseEntity<ApiResponse<CommentListResponse>> getComments(
            @PathVariable Long roomId,
            @PathVariable Long agendaId,
            @AuthenticationPrincipal LoginUser loginUser) {
        CommentListResponse response =
                agendaService.getComments(roomId, agendaId, loginUser.userId());
        return ResponseEntity.ok(ApiResponse.success("COMMENTS_FETCHED", response));
    }

    /** V-06 안건 코멘트 작성 */
    @PostMapping("/{agendaId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> addComment(
            @PathVariable Long roomId,
            @PathVariable Long agendaId,
            @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal LoginUser loginUser) {
        CommentResponse response =
                agendaService.addComment(roomId, agendaId, loginUser.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("COMMENT_CREATED", "코멘트를 남겼습니다.", response));
    }

    /** V-07 예산 선지 추가 */
    @PostMapping("/{agendaId}/options")
    public ResponseEntity<ApiResponse<AgendaDetailResponse>> addBudgetOption(
            @PathVariable Long roomId,
            @PathVariable Long agendaId,
            @Valid @RequestBody AddOptionRequest request,
            @AuthenticationPrincipal LoginUser loginUser) {
        AgendaDetailResponse response =
                agendaService.addBudgetOption(roomId, agendaId, loginUser.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("OPTION_CREATED", "선지를 추가했습니다.", response));
    }

    /** V-08 예산 선지 삭제 */
    @DeleteMapping("/{agendaId}/options/{optionId}")
    public ResponseEntity<ApiResponse<AgendaDetailResponse>> deleteBudgetOption(
            @PathVariable Long roomId,
            @PathVariable Long agendaId,
            @PathVariable Long optionId,
            @AuthenticationPrincipal LoginUser loginUser) {
        AgendaDetailResponse response =
                agendaService.deleteBudgetOption(roomId, agendaId, optionId, loginUser.userId());
        return ResponseEntity.ok(ApiResponse.success("OPTION_DELETED", "선지를 삭제했습니다.", response));
    }

    /** V-04 다음 회차 열기 */
    @PostMapping("/{agendaId}/next-round")
    public ResponseEntity<ApiResponse<AgendaDetailResponse>> openNextRound(
            @PathVariable Long roomId,
            @PathVariable Long agendaId,
            @AuthenticationPrincipal LoginUser loginUser) {
        AgendaDetailResponse response =
                agendaService.openNextRound(roomId, agendaId, loginUser.userId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("AGENDA_ROUND_OPENED", "다음 회차 투표를 열었습니다.", response));
    }
}
