package world.chakchak.domain.room.controller;

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
import world.chakchak.domain.room.service.RoomLeaveService;
import world.chakchak.domain.room.service.RoomService;
import world.chakchak.global.response.ApiResponse;
import world.chakchak.global.security.LoginUser;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final RoomLeaveService roomLeaveService;

    @GetMapping("/invites/{inviteCode}")
    public ResponseEntity<ApiResponse<InvitePreviewResponse>> getInvitePreview(
            @AuthenticationPrincipal LoginUser loginUser, @PathVariable String inviteCode) {
        Long userId = loginUser == null ? null : loginUser.userId();
        InvitePreviewResponse response = roomService.getInvitePreview(inviteCode, userId);
        return ResponseEntity.ok(ApiResponse.success("INVITE_PREVIEW_FETCHED", response));
    }

    @PostMapping("/invites/{inviteCode}/join")
    public ResponseEntity<ApiResponse<JoinRoomResponse>> joinByInviteCode(
            @AuthenticationPrincipal LoginUser loginUser, @PathVariable String inviteCode) {
        JoinRoomResponse response = roomService.joinByInviteCode(inviteCode, loginUser.userId());
        return ResponseEntity.ok(ApiResponse.success("ROOM_JOINED", "여행방에 합류했습니다.", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<RoomListResponse>> getMyRooms(
            @AuthenticationPrincipal LoginUser loginUser) {
        RoomListResponse response = roomService.getMyRooms(loginUser.userId());
        return ResponseEntity.ok(ApiResponse.success("ROOMS_FETCHED", response));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponse<RoomDetailResponse>> getRoomDetail(
            @AuthenticationPrincipal LoginUser loginUser, @PathVariable Long roomId) {
        RoomDetailResponse response = roomService.getRoomDetail(roomId, loginUser.userId());
        return ResponseEntity.ok(ApiResponse.success("ROOM_FETCHED", response));
    }

    @PostMapping("/{roomId}/seen")
    public ResponseEntity<ApiResponse<Void>> markSeen(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long roomId,
            @Valid @RequestBody SeenRequest request) {
        roomService.markSeen(roomId, loginUser.userId(), request.seenUntil());
        return ResponseEntity.ok(
                ApiResponse.successWithoutData("ROOM_SEEN_UPDATED", "확인 시각을 갱신했습니다."));
    }

    @DeleteMapping("/{roomId}/members/me")
    public ResponseEntity<ApiResponse<Void>> leave(
            @AuthenticationPrincipal LoginUser loginUser, @PathVariable Long roomId) {
        roomLeaveService.leave(roomId, loginUser.userId());
        return ResponseEntity.ok(ApiResponse.successWithoutData("ROOM_LEFT", "방에서 나갔습니다."));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RoomCreatedResponse>> create(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody CreateRoomRequest request) {
        RoomCreatedResponse response = roomService.create(loginUser.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("ROOM_CREATED", "여행방을 만들었습니다.", response));
    }
}
