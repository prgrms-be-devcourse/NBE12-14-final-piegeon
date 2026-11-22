package world.chakchak.domain.schedule.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import world.chakchak.domain.schedule.dto.AddItemRequest;
import world.chakchak.domain.schedule.dto.AddItemResponse;
import world.chakchak.domain.schedule.dto.DayChangeResponse;
import world.chakchak.domain.schedule.dto.MoveItemRequest;
import world.chakchak.domain.schedule.dto.ScheduleBoardResponse;
import world.chakchak.domain.schedule.dto.UpdateItemRequest;
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

    /** B-02 블록 추가 */
    @Operation(
            summary = "블록 추가 (B-02)",
            description = "확정된 장소를 afterItemId 블록 뒤(null이면 맨 앞)에 넣고, 그날의 바뀐 모습을 돌려준다.")
    @PostMapping("/schedule-days/{dayId}/items")
    public ResponseEntity<ApiResponse<AddItemResponse>> addItem(
            @PathVariable Long roomId,
            @PathVariable Long dayId,
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody AddItemRequest request) {
        AddItemResponse response =
                scheduleBoardService.addItem(roomId, dayId, loginUser.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("SCHEDULE_ITEM_CREATED", response));
    }

    /** B-03 블록 이동 */
    @Operation(
            summary = "블록 이동 (B-03)",
            description = "같은 날 안에서 블록을 afterItemId 블록 뒤(null이면 맨 앞)로 옮기고, 그날의 바뀐 모습을 돌려준다.")
    @PatchMapping("/schedule-items/{itemId}/position")
    public ResponseEntity<ApiResponse<DayChangeResponse>> moveItem(
            @PathVariable Long roomId,
            @PathVariable Long itemId,
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody MoveItemRequest request) {
        DayChangeResponse response =
                scheduleBoardService.moveItem(roomId, itemId, loginUser.userId(), request);
        return ResponseEntity.ok(ApiResponse.success("SCHEDULE_ITEM_MOVED", response));
    }

    /** B-04 블록 수정 */
    @Operation(
            summary = "블록 수정 (B-04)",
            description = "블록의 시간과 메모를 고친다. 시간이 앞뒤 블록과 어긋나면 warnings로 알려 준다.")
    @PatchMapping("/schedule-items/{itemId}")
    public ResponseEntity<ApiResponse<DayChangeResponse>> updateItem(
            @PathVariable Long roomId,
            @PathVariable Long itemId,
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody UpdateItemRequest request) {
        DayChangeResponse response =
                scheduleBoardService.updateItem(roomId, itemId, loginUser.userId(), request);
        return ResponseEntity.ok(ApiResponse.success("SCHEDULE_ITEM_UPDATED", response));
    }
}
