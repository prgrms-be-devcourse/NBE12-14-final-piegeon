package world.chakchak.domain.room.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import world.chakchak.domain.room.dto.CreateRoomRequest;
import world.chakchak.domain.room.dto.RoomCreatedResponse;
import world.chakchak.domain.room.service.RoomService;
import world.chakchak.global.response.ApiResponse;
import world.chakchak.global.security.LoginUser;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    public ResponseEntity<ApiResponse<RoomCreatedResponse>> create(
            @AuthenticationPrincipal LoginUser loginUser,
            @Valid @RequestBody CreateRoomRequest request) {
        RoomCreatedResponse response = roomService.create(loginUser.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("ROOM_CREATED", "여행방을 만들었습니다.", response));
    }
}
