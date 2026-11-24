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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import world.chakchak.domain.changelog.dto.ChangeLogItemResponse;
import world.chakchak.domain.changelog.dto.ChangeLogResyncRequiredResponse;
import world.chakchak.domain.changelog.dto.ChangeLogStreamReadyResponse;
import world.chakchak.domain.changelog.event.RoomEvent;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.service.RoomMemberService;
import world.chakchak.global.error.BusinessException;
import world.chakchak.global.error.CommonErrorCode;

@Service
@RequiredArgsConstructor
public class ChangeLogStreamService {

    private static final long KEEP_ALIVE_DELAY_MILLIS = 25_000L;
    private static final long CONNECTION_TIMEOUT_MILLIS = 30 * 60 * 1_000L;
    private static final int MAX_CONNECTIONS_PER_MEMBER = 3;

    private final ChangeLogReplayService changeLogReplayService;
    private final RoomMemberService roomMemberService;
    private final Map<Long, Set<StreamConnection>> connections = new ConcurrentHashMap<>();

    public SseEmitter connect(Long roomId, Long userId, String lastEventIdHeader) {
        RoomMember member = roomMemberService.getActiveMember(roomId, userId);
        Long memberId = member.getId();
        Long lastEventId = parseLastEventId(lastEventIdHeader);
        Set<StreamConnection> memberConnections =
                connections.computeIfAbsent(roomId, ignored -> ConcurrentHashMap.newKeySet());
        long memberConnectionCount =
                memberConnections.stream().filter(c -> c.memberId.equals(memberId)).count();
        if (memberConnectionCount >= MAX_CONNECTIONS_PER_MEMBER) {
            throw new BusinessException(CommonErrorCode.TOO_MANY_STREAMS);
        }
        SseEmitter emitter = new SseEmitter(CONNECTION_TIMEOUT_MILLIS);
        StreamConnection connection = new StreamConnection(roomId, memberId, userId, emitter);
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
        if (!roomMemberService.isActiveMember(roomId, memberId)) {
            remove(connection);
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        emitter.onCompletion(() -> remove(connection));
        emitter.onTimeout(() -> remove(connection));
        emitter.onError(ignored -> remove(connection));

        try {
            ChangeLogReplayService.Snapshot replay =
                    changeLogReplayService.getSnapshot(roomId, lastEventId);
            connection.initialize(
                    replay.latestActivityId(),
                    lastEventId,
                    replay.activities(),
                    replay.resyncRequired());
        } catch (RuntimeException e) {
            connection.complete();
            throw e;
        }
        return emitter;
    }

    public void closeConnectionsForMember(Long roomId, Long memberId) {
        Set<StreamConnection> roomConnections = connections.get(roomId);
        if (roomConnections != null)
            roomConnections.stream()
                    .filter(c -> c.memberId.equals(memberId))
                    .toList()
                    .forEach(StreamConnection::complete);
    }

    public void closeConnectionsForUser(Long userId) {
        connections.values().stream()
                .flatMap(Set::stream)
                .filter(c -> c.userId.equals(userId))
                .toList()
                .forEach(StreamConnection::complete);
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

    public void publish(RoomEvent event) {
        Set<StreamConnection> roomConnections = connections.get(event.roomId());
        if (roomConnections == null) {
            return;
        }
        for (StreamConnection connection : roomConnections) {
            connection.offer(event);
        }
    }

    @Scheduled(fixedDelay = KEEP_ALIVE_DELAY_MILLIS)
    public void keepConnectionsAlive() {
        connections.values().stream().flatMap(Set::stream).forEach(StreamConnection::keepAlive);
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
        private final Long userId;
        private final SseEmitter emitter;
        private final TreeMap<Long, ChangeLogItemResponse> pending = new TreeMap<>();
        private boolean initialized;
        private long lastSentActivityId;

        private StreamConnection(Long roomId, Long memberId, Long userId, SseEmitter emitter) {
            this.roomId = roomId;
            this.memberId = memberId;
            this.userId = userId;
            this.emitter = emitter;
        }

        private synchronized void initialize(
                Long latestActivityId,
                Long lastEventId,
                List<ChangeLogItemResponse> replay,
                boolean resyncRequired) {
            send(
                    SseEmitter.event()
                            .name("connected")
                            .data(new ChangeLogStreamReadyResponse(latestActivityId)));
            if (resyncRequired) {
                lastSentActivityId = latestActivityId == null ? 0L : latestActivityId;
                send(
                        SseEmitter.event()
                                .name("resync.required")
                                .data(new ChangeLogResyncRequiredResponse(latestActivityId)));
            } else {
                lastSentActivityId = lastEventId == null ? 0L : lastEventId;
                replay.forEach(this::sendActivity);
                lastSentActivityId =
                        Math.max(
                                lastSentActivityId,
                                latestActivityId == null ? 0L : latestActivityId);
            }
            pending.tailMap(lastSentActivityId, false).values().forEach(this::sendActivity);
            pending.clear();
            initialized = true;
        }

        private synchronized void offer(RoomEvent event) {
            if (!initialized) {
                if (event.changeLogId() != null
                        && event.data() instanceof ChangeLogItemResponse activity) {
                    pending.put(activity.logId(), activity);
                }
                return;
            }
            sendRoomEvent(event);
        }

        private synchronized void keepAlive() {
            if (initialized) {
                send(SseEmitter.event().comment("ping"));
            }
        }

        private synchronized void complete() {
            emitter.complete();
        }

        private void sendActivity(ChangeLogItemResponse activity) {
            if (activity.logId() <= lastSentActivityId) {
                return;
            }
            send(
                    SseEmitter.event()
                            .id(activity.logId().toString())
                            .name("activity")
                            .data(activity));
            lastSentActivityId = activity.logId();
        }

        private void sendRoomEvent(RoomEvent event) {
            SseEmitter.SseEventBuilder builder =
                    SseEmitter.event().name(event.name()).data(event.data());
            if (event.changeLogId() != null) {
                builder.id(event.changeLogId().toString());
                lastSentActivityId = Math.max(lastSentActivityId, event.changeLogId());
            }
            send(builder);
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
