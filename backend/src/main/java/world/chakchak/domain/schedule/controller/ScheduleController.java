package world.chakchak.domain.schedule.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import world.chakchak.domain.schedule.dto.ScheduleBoardResponse;
import world.chakchak.domain.schedule.service.ScheduleBoardService;
import world.chakchak.global.response.ApiResponse;
import world.chakchak.global.security.LoginUser;

@Tag(name = "일정 보드", description = "일차별 일정 보드 (B-01~B-05)")
@RestController
@RequestMapping("/api/rooms/{roomId}")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleBoardService scheduleBoardService;

    /** B-01 보드 조회 */
    @Operation(summary = "보드 조회 (B-01)", description = "1일차부터 날마다 날짜, 버전, 블록을 화면 순서대로 돌려준다.")
    @GetMapping("/schedule")
    public ResponseEntity<ApiResponse<ScheduleBoardResponse>> getBoard(
            @PathVariable Long roomId, @AuthenticationPrincipal LoginUser loginUser) {
        ScheduleBoardResponse response = scheduleBoardService.getBoard(roomId, loginUser.userId());
        return ResponseEntity.ok(ApiResponse.success("SCHEDULE_FETCHED", response));
    }
}
