package world.chakchak.domain.room.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import world.chakchak.domain.room.repository.RoomRepository;

/** 매일 만료된 여행방을 찾아 방 단위로 삭제한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class RoomCleanupScheduler {

    private final RoomRepository roomRepository;
    private final RoomCleanupService roomCleanupService;
    private final Clock clock;

    @Scheduled(cron = "${app.scheduler.room-cleanup-cron}", zone = "Asia/Seoul")
    public void deleteExpiredRooms() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> roomIds;
        try {
            roomIds =
                    roomRepository.findRoomIdsExpiredForCleanup(
                            now.minusDays(7), now.toLocalDate().minusDays(14));
        } catch (RuntimeException e) {
            log.error("만료 여행방 삭제 대상 조회 실패", e);
            return;
        }

        for (Long roomId : roomIds) {
            try {
                roomCleanupService.deleteRoom(roomId);
            } catch (RuntimeException e) {
                log.error("만료 여행방 삭제 실패: roomId={}", roomId, e);
            }
        }
    }
}
