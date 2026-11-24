package world.chakchak.domain.changelog.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.changelog.dto.ChangeLogItemResponse;
import world.chakchak.domain.changelog.repository.ChangeLogRepository;

@Service
@RequiredArgsConstructor
public class ChangeLogReplayService {

    private final ChangeLogRepository changeLogRepository;

    @Transactional(readOnly = true)
    public Snapshot getSnapshot(Long roomId, Long lastEventId) {
        Long latestActivityId =
                changeLogRepository
                        .findTopByRoom_IdOrderByIdDesc(roomId)
                        .map(changeLog -> changeLog.getId())
                        .orElse(null);
        if (lastEventId == null) {
            return new Snapshot(latestActivityId, List.of(), false);
        }
        if (latestActivityId == null) {
            return new Snapshot(latestActivityId, List.of(), lastEventId != 0L);
        }
        if (lastEventId > latestActivityId) {
            return new Snapshot(latestActivityId, List.of(), true);
        }

        List<ChangeLogItemResponse> missing =
                changeLogRepository
                        .findTop201ByRoom_IdAndIdGreaterThanOrderByIdAsc(roomId, lastEventId)
                        .stream()
                        .map(ChangeLogItemResponse::from)
                        .toList();
        if (missing.size() > 200) {
            return new Snapshot(latestActivityId, List.of(), true);
        }
        return new Snapshot(latestActivityId, missing, false);
    }

    public record Snapshot(
            Long latestActivityId,
            List<ChangeLogItemResponse> activities,
            boolean resyncRequired) {}
}
