package world.chakchak.domain.agenda.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import world.chakchak.domain.agenda.dto.AgendaDetailResponse;
import world.chakchak.domain.agenda.dto.AgendaListResponse;
import world.chakchak.domain.agenda.service.AgendaService;
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
}
