package world.chakchak.domain.changelog.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import world.chakchak.domain.changelog.dto.ChangeLogItemResponse;
import world.chakchak.domain.changelog.dto.ChangeLogResyncRequiredResponse;
import world.chakchak.domain.changelog.dto.ChangeLogStreamReadyResponse;
import world.chakchak.domain.changelog.repository.ChangeLogRepository;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.service.RoomMemberService;
import world.chakchak.global.error.BusinessException;
import world.chakchak.global.error.CommonErrorCode;

@Service
@RequiredArgsConstructor
public class ChangeLogStreamService {

    private static final long KEEP_ALIVE_DELAY_MILLIS = 15_000L;

    private final ChangeLogRepository changeLogRepository;
    private final RoomMemberService roomMemberService;
    private final Map<Long, Set<StreamConnection>> connections = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public SseEmitter connect(Long roomId, Long userId, String lastEventIdHeader) {
        RoomMember member = roomMemberService.getActiveMember(roomId, userId);
        Long memberId = member.getId();
        Long lastEventId = parseLastEventId(lastEventIdHeader);
        SseEmitter emitter = new SseEmitter(0L);
        StreamConnection connection = new StreamConnection(roomId, memberId, emitter);
        connections.compute(
                roomId,
                (ignored, roomConnections) -> {
                    Set<StreamConnection> activeConnections =
                            roomConnections == null
                                    ? ConcurrentHashMap.newKeySet()
                                    : roomConnections;
                    activeConnections.add(connection);
                    return activeConnections;
                });
        emitter.onCompletion(() -> remove(connection));
        emitter.onTimeout(() -> remove(connection));
        emitter.onError(ignored -> remove(connection));

        try {
            Long latestActivityId =
                    changeLogRepository
                            .findTopByRoom_IdOrderByIdDesc(roomId)
                            .map(changeLog -> changeLog.getId())
                            .orElse(null);
            Replay replay = replay(roomId, lastEventId, latestActivityId);
            connection.initialize(
                    latestActivityId, lastEventId, replay.activities(), replay.resyncRequired());
        } catch (RuntimeException e) {
            connection.complete();
            throw e;
        }
        return emitter;
    }

    private Replay replay(Long roomId, Long lastEventId, Long latestActivityId) {
        if (lastEventId == null) {
            return new Replay(List.of(), false);
        }
        if (latestActivityId == null) {
            return lastEventId == 0L
                    ? new Replay(List.of(), false)
                    : new Replay(List.of(), true);
        }
        if (lastEventId > latestActivityId) {
            return new Replay(List.of(), true);
        }
        var missing =
                changeLogRepository.findTop201ByRoom_IdAndIdGreaterThanOrderByIdAsc(
                        roomId, lastEventId);
        if (missing.size() > 200) {
            return new Replay(List.of(), true);
        }
        return new Replay(missing.stream().map(ChangeLogItemResponse::from).toList(), false);
    }

    private Long parseLastEventId(String lastEventIdHeader) {
        if (lastEventIdHeader == null || lastEventIdHeader.isBlank()) {
            return null;
        }
        try {
            long lastEventId = Long.parseLong(lastEventIdHeader);
            if (lastEventId < 0) {
                throw new NumberFormatException("negative id");
            }
            return lastEventId;
        } catch (NumberFormatException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }

    private record Replay(List<ChangeLogItemResponse> activities, boolean resyncRequired) {}

    public void publish(ChangeLogItemResponse activity) {
        Set<StreamConnection> roomConnections = connections.get(activity.roomId());
        if (roomConnections == null) {
            return;
        }
        for (StreamConnection connection : roomConnections) {
            if (isActiveMember(connection)) {
                connection.offer(activity);
            } else {
                connection.complete();
            }
        }
    }

    @Scheduled(fixedDelay = KEEP_ALIVE_DELAY_MILLIS)
    public void keepConnectionsAlive() {
        connections.values().stream()
                .flatMap(Set::stream)
                .forEach(connection -> {
                    if (isActiveMember(connection)) {
                        connection.keepAlive();
                    } else {
                        connection.complete();
                    }
                });
    }

    private boolean isActiveMember(StreamConnection connection) {
        try {
            return roomMemberService.isActiveMember(connection.roomId, connection.memberId);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void remove(StreamConnection connection) {
        connections.computeIfPresent(
                connection.roomId,
                (ignored, roomConnections) -> {
                    roomConnections.remove(connection);
                    return roomConnections.isEmpty() ? null : roomConnections;
                });
    }

    private static class StreamConnection {

        private final Long roomId;
        private final Long memberId;
        private final SseEmitter emitter;
        private final TreeMap<Long, ChangeLogItemResponse> pending = new TreeMap<>();
        private boolean initialized;
        private long lastSentActivityId;

        private StreamConnection(Long roomId, Long memberId, SseEmitter emitter) {
            this.roomId = roomId;
            this.memberId = memberId;
            this.emitter = emitter;
        }

        private synchronized void initialize(
                Long latestActivityId,
                Long lastEventId,
                List<ChangeLogItemResponse> replay,
                boolean resyncRequired) {
            if (resyncRequired) {
                lastSentActivityId = latestActivityId == null ? 0L : latestActivityId;
                send(
                        SseEmitter.event()
                                .name("resync.required")
                                .data(new ChangeLogResyncRequiredResponse(latestActivityId)));
            } else {
                lastSentActivityId = lastEventId == null ? 0L : lastEventId;
                replay.forEach(this::sendActivity);
                lastSentActivityId = Math.max(
                        lastSentActivityId, latestActivityId == null ? 0L : latestActivityId);
            }
            send(
                    SseEmitter.event()
                            .name("ready")
                            .data(new ChangeLogStreamReadyResponse(latestActivityId)));
            pending.tailMap(lastSentActivityId, false).values().forEach(this::sendActivity);
            pending.clear();
            initialized = true;
        }

        private synchronized void offer(ChangeLogItemResponse activity) {
            if (!initialized) {
                pending.put(activity.id(), activity);
                return;
            }
            sendActivity(activity);
        }

        private synchronized void keepAlive() {
            if (initialized) {
                send(SseEmitter.event().comment("keep-alive"));
            }
        }

        private synchronized void complete() {
            emitter.complete();
        }

        private void sendActivity(ChangeLogItemResponse activity) {
            if (activity.id() <= lastSentActivityId) {
                return;
            }
            send(
                    SseEmitter.event()
                            .id(activity.id().toString())
                            .name("activity")
                            .data(activity));
            lastSentActivityId = activity.id();
        }

        private void send(SseEmitter.SseEventBuilder event) {
            try {
                emitter.send(event);
            } catch (IOException | IllegalStateException e) {
                emitter.completeWithError(e);
            }
        }
    }
}
