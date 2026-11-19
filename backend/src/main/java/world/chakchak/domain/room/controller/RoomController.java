package world.chakchak.domain.room.controller;

import jakarta.servlet.http.HttpServletRequest;
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
import world.chakchak.domain.room.dto.CreateRoomRequest;
import world.chakchak.domain.room.dto.InvitePreviewResponse;
import world.chakchak.domain.room.dto.RoomCreatedResponse;
import world.chakchak.domain.room.dto.RoomDetailResponse;
import world.chakchak.domain.room.dto.RoomListResponse;
import world.chakchak.domain.room.service.RoomService;
import world.chakchak.global.response.ApiResponse;
import world.chakchak.global.security.LoginUser;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @GetMapping("/invites/{inviteCode}")
    public ResponseEntity<ApiResponse<InvitePreviewResponse>> getInvitePreview(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable String inviteCode,
            HttpServletRequest request) {
        Long userId = loginUser == null ? null : loginUser.userId();
        InvitePreviewResponse response =
                roomService.getInvitePreview(inviteCode, userId, request.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success("INVITE_PREVIEW_FETCHED", response));
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

    @PostMapping
    public ResponseEntity<ApiResponse<RoomCreatedResponse>> create(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody CreateRoomRequest request) {
        RoomCreatedResponse response = roomService.create(loginUser.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("ROOM_CREATED", "여행방을 만들었습니다.", response));
    }
}
