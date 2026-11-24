package world.chakchak.domain.changelog.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import world.chakchak.domain.changelog.dto.ChangeLogConditionRequest;
import world.chakchak.domain.changelog.dto.ChangeLogPageResponse;
import world.chakchak.domain.changelog.service.ChangeLogStreamService;
import world.chakchak.domain.changelog.service.ChangeLogTimelineService;
import world.chakchak.global.error.BusinessException;
import world.chakchak.global.response.ApiResponse;
import world.chakchak.global.security.LoginUser;

@RestController
@RequestMapping("/api/rooms/{roomId}")
@RequiredArgsConstructor
@Validated
public class ChangeLogController {

    private final ChangeLogStreamService changeLogStreamService;
    private final ChangeLogTimelineService changeLogTimelineService;

    @GetMapping("/change-logs")
    public ResponseEntity<ApiResponse<ChangeLogPageResponse>> getTimeline(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long roomId,
            @Valid @ModelAttribute ChangeLogConditionRequest condition) {
        ChangeLogPageResponse response =
                changeLogTimelineService.getTimeline(
                        roomId, loginUser.userId(), condition.cursor(), condition.sizeOrDefault());
        return ResponseEntity.ok(ApiResponse.success("CHANGE_LOGS_FETCHED", response));
    }

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable Long roomId,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {
        SseEmitter emitter =
                changeLogStreamService.connect(roomId, loginUser.userId(), lastEventId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header("X-Accel-Buffering", "no")
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleStreamBusinessException(
            BusinessException exception) {
        return ResponseEntity.status(exception.getErrorCode().getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(exception.getErrorCode(), exception.getData()));
    }
}
