package world.chakchak.domain.agenda.event;

import world.chakchak.domain.room.entity.RoomStatus;

/**
 * SSE {@code room.status_changed}의 data (명세 E-01). 지금은 D-DAY 최종 집계에서 방이 만료될 때만 보낸다.
 *
 * <p>방 만료는 방 활동 기록에 남기지 않으므로 id 없이 보내고, 재연결 때 다시 보내지 않는다.
 */
public record RoomStatusChangedEvent(RoomStatus status) {}
