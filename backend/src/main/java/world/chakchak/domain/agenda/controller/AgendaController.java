package world.chakchak.domain.agenda.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import world.chakchak.domain.agenda.dto.CreateAgendaRequest;
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
@Tag(name = "안건", description = "여행방 안건 조회, 투표 및 의견 관리 API")
public class AgendaController {

    private final AgendaService agendaService;

    /** V-01 안건 목록 */
    @Operation(summary = "안건 목록 조회", description = "해당 여행방의 안건 목록을 조회한다.")
    @GetMapping
    public ResponseEntity<ApiResponse<AgendaListResponse>> getAgendas(
            @PathVariable Long roomId, @AuthenticationPrincipal LoginUser loginUser) {
        AgendaListResponse response = agendaService.getAgendas(roomId, loginUser.userId());
        return ResponseEntity.ok(ApiResponse.success("AGENDAS_FETCHED", response));
    }

    /** V-09 자유 안건 열기 */
    @Operation(
            summary = "자유 안건 열기",
            description =
                    "설문 항목 밖의 주제로 안건을 연다. 설문이 마감된 뒤(설문 마감 후 SURVEYING, PLANNING)에만 열 수 있다. "
                            + "같은 방에 같은 제목의 안건이 있으면 열 수 없다.")
    @PostMapping
    public ResponseEntity<ApiResponse<AgendaDetailResponse>> openCustomAgenda(
            @PathVariable Long roomId,
            @Valid @RequestBody CreateAgendaRequest request,
            @AuthenticationPrincipal LoginUser loginUser) {
        AgendaDetailResponse response =
                agendaService.openCustomAgenda(roomId, loginUser.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("AGENDA_CREATED", "안건을 열었습니다.", response));
    }

    /** V-02 안건 상세 */
    @Operation(
            summary = "안건 상세 조회",
            description = "안건 ID로 안건 상세 정보를 조회한다. " + "투표 현황과 안건 관련 정보를 반환한다.")
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
    @Operation(
            summary = "안건 투표",
            description = "안건의 선택지에 투표한다. " + "이미 투표한 경우 다른 선택지로 투표를 변경할 수 있다.")
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
    @Operation(summary = "안건 코멘트 목록 조회", description = "해당 안건에 작성된 코멘트 목록을 조회한다.")
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
    @Operation(summary = "안건 코멘트 작성", description = "해당 안건에 새로운 코멘트를 작성한다. " + "작성한 코멘트 정보를 반환한다.")
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
    @Operation(
            summary = "예산 안건 선지 추가",
            description = "예산 안건에 새로운 선택지를 추가한다. " + "추가된 선택지를 포함한 안건 상세 정보를 반환한다.")
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
    @Operation(
            summary = "예산 안건 선지 삭제",
            description = "예산 안건에서 지정한 선택지를 삭제한다. " + "삭제 후 안건 상세 정보를 반환한다.")
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
    @Operation(
            summary = "다음 회차 투표 열기",
            description = "해당 안건의 다음 회차 투표를 연다. " + "새로운 회차가 생성되면 안건 상세 정보를 반환한다.")
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
