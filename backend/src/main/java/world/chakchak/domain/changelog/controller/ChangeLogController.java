package world.chakchak.domain.changelog.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import world.chakchak.domain.changelog.dto.ChangeLogPageResponse;
import world.chakchak.domain.changelog.service.ChangeLogStreamService;
import world.chakchak.domain.changelog.service.ChangeLogTimelineService;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.service.RoomMemberService;
import world.chakchak.global.response.ApiResponse;
import world.chakchak.global.security.LoginUser;

@RestController
@RequestMapping("/api/rooms/{roomId}/change-logs")
@RequiredArgsConstructor
@Validated
public class ChangeLogController {

    private final RoomMemberService roomMemberService;
    private final ChangeLogStreamService changeLogStreamService;
    private final ChangeLogTimelineService changeLogTimelineService;

    @GetMapping
    public ResponseEntity<ApiResponse<ChangeLogPageResponse>> getTimeline(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long roomId,
            @RequestParam(required = false) @Min(1) Long cursor,
            @RequestParam(required = false) @Min(1) Long throughId,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size) {
        ChangeLogPageResponse response =
                changeLogTimelineService.getTimeline(
                        roomId, loginUser.userId(), cursor, throughId, size);
        return ResponseEntity.ok(ApiResponse.success("CHANGE_LOGS_FETCHED", response));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long roomId,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {
        RoomMember member = roomMemberService.getActiveMember(roomId, loginUser.userId());
        return changeLogStreamService.connect(roomId, member.getId(), lastEventId);
    }
}
