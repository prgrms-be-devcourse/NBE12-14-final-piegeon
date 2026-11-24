package world.chakchak.domain.changelog.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.changelog.dto.ChangeLogItemResponse;
import world.chakchak.domain.changelog.dto.ChangeLogPageResponse;
import world.chakchak.domain.changelog.entity.ChangeLog;
import world.chakchak.domain.changelog.repository.ChangeLogRepository;
import world.chakchak.domain.room.service.RoomMemberService;

@Service
@RequiredArgsConstructor
public class ChangeLogTimelineService {

    private final ChangeLogRepository changeLogRepository;
    private final RoomMemberService roomMemberService;

    @Transactional(readOnly = true)
    public ChangeLogPageResponse getTimeline(
            Long roomId, Long userId, Long cursor, Long requestedSize) {
        roomMemberService.getActiveMember(roomId, userId);
        long pageSize = requestedSize == null ? 20L : requestedSize;
        Slice<ChangeLog> page =
                changeLogRepository.findTimelinePage(
                        roomId, cursor, PageRequest.of(0, (int) pageSize));
        List<ChangeLogItemResponse> items =
                page.getContent().stream().map(ChangeLogItemResponse::from).toList();
        Long nextCursor = page.hasNext() ? items.getLast().logId() : null;
        return new ChangeLogPageResponse(items, nextCursor);
    }
}
