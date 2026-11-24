package world.chakchak.domain.room.controller;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import world.chakchak.domain.room.dto.CreateRoomRequest;
import world.chakchak.domain.room.dto.InvitePreviewResponse;
import world.chakchak.domain.room.dto.JoinRoomResponse;
import world.chakchak.domain.room.dto.RoomCreatedResponse;
import world.chakchak.domain.room.dto.RoomDetailResponse;
import world.chakchak.domain.room.dto.RoomListResponse;
import world.chakchak.domain.room.dto.SeenRequest;
import world.chakchak.domain.room.dto.SummaryResponse;
import world.chakchak.domain.room.service.RoomLeaveService;
import world.chakchak.domain.room.service.RoomService;
import world.chakchak.domain.room.service.RoomSummaryService;
import world.chakchak.global.response.ApiResponse;
import world.chakchak.global.security.LoginUser;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Tag(name = "여행방", description = "여행방 생성, 조회, 참여 및 관리 API")
public class RoomController {

    private final RoomService roomService;
    private final RoomLeaveService roomLeaveService;
    private final RoomSummaryService roomSummaryService;

    @Operation(
            summary = "초대 미리보기 조회",
            description = "초대 코드로 여행방 정보를 미리 조회한다. " + "로그인한 사용자의 경우 참여 여부 등의 정보를 함께 반환한다.")
    @GetMapping("/invites/{inviteCode}")
    public ResponseEntity<ApiResponse<InvitePreviewResponse>> getInvitePreview(
            @AuthenticationPrincipal LoginUser loginUser, @PathVariable String inviteCode) {
        Long userId = loginUser == null ? null : loginUser.userId();
        InvitePreviewResponse response = roomService.getInvitePreview(inviteCode, userId);
        return ResponseEntity.ok(ApiResponse.success("INVITE_PREVIEW_FETCHED", response));
    }

    @Operation(
            summary = "초대 코드로 여행방 참여",
            description = "초대 코드에 해당하는 여행방에 참여한다. " + "참여가 완료되면 참여 결과를 반환한다.")
    @PostMapping("/invites/{inviteCode}/join")
    public ResponseEntity<ApiResponse<JoinRoomResponse>> joinByInviteCode(
            @AuthenticationPrincipal LoginUser loginUser, @PathVariable String inviteCode) {
        JoinRoomResponse response = roomService.joinByInviteCode(inviteCode, loginUser.userId());
        return ResponseEntity.ok(ApiResponse.success("ROOM_JOINED", "여행방에 합류했습니다.", response));
    }

    @Operation(
            summary = "내 여행방 목록 조회",
            description = "로그인한 사용자가 참여 중인 여행방 목록을 조회한다. " + "조회 결과로 여행방 목록을 반환한다.")
    @GetMapping
    public ResponseEntity<ApiResponse<RoomListResponse>> getMyRooms(
            @AuthenticationPrincipal LoginUser loginUser) {
        RoomListResponse response = roomService.getMyRooms(loginUser.userId());
        return ResponseEntity.ok(ApiResponse.success("ROOMS_FETCHED", response));
    }

    @Operation(
            summary = "여행방 상세 조회",
            description = "해당 여행방의 상세 정보를 조회한다. " + "조회 결과로 여행방 상세 정보를 반환한다.")
    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponse<RoomDetailResponse>> getRoomDetail(
            @AuthenticationPrincipal LoginUser loginUser, @PathVariable Long roomId) {
        RoomDetailResponse response = roomService.getRoomDetail(roomId, loginUser.userId());
        return ResponseEntity.ok(ApiResponse.success("ROOM_FETCHED", response));
    }

    @Operation(
            summary = "여행방 요약 조회",
            description = "해당 여행방의 요약 정보를 조회한다. " + "조회 결과로 여행방 요약 정보를 반환한다.")
    @GetMapping("/{roomId}/summary")
    public ResponseEntity<ApiResponse<SummaryResponse>> getSummary(
            @AuthenticationPrincipal LoginUser loginUser, @PathVariable Long roomId) {
        SummaryResponse response = roomSummaryService.getSummary(roomId, loginUser.userId());
        return ResponseEntity.ok(ApiResponse.success("ROOM_SUMMARY_FETCHED", response));
    }

    @Operation(
            summary = "여행방 확인 시각 갱신",
            description = "해당 여행방의 확인 시각을 요청에 포함된 시각으로 갱신한다. " + "갱신이 완료되면 성공 응답을 반환한다.")
    @PostMapping("/{roomId}/seen")
    public ResponseEntity<ApiResponse<Void>> markSeen(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long roomId,
            @Valid @RequestBody SeenRequest request) {
        roomService.markSeen(roomId, loginUser.userId(), request.seenUntil());
        return ResponseEntity.ok(
                ApiResponse.successWithoutData("ROOM_SEEN_UPDATED", "확인 시각을 갱신했습니다."));
    }

    @Operation(
            summary = "여행방 나가기",
            description = "로그인한 사용자가 해당 여행방에서 나간다. " + "나가기가 완료되면 성공 응답을 반환한다.")
    @DeleteMapping("/{roomId}/members/me")
    public ResponseEntity<ApiResponse<Void>> leave(
            @AuthenticationPrincipal LoginUser loginUser, @PathVariable Long roomId) {
        roomLeaveService.leave(roomId, loginUser.userId());
        return ResponseEntity.ok(ApiResponse.successWithoutData("ROOM_LEFT", "방에서 나갔습니다."));
    }

    @Operation(
            summary = "여행방 생성",
            description = "요청 정보를 바탕으로 새로운 여행방을 생성한다. " + "생성이 완료되면 생성된 여행방 정보를 반환한다.")
    @PostMapping
    public ResponseEntity<ApiResponse<RoomCreatedResponse>> create(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody CreateRoomRequest request) {
        RoomCreatedResponse response = roomService.create(loginUser.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("ROOM_CREATED", "여행방을 만들었습니다.", response));
    }
}
