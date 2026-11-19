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
import world.chakchak.global.error.BusinessException;
import world.chakchak.global.error.CommonErrorCode;

@Service
@RequiredArgsConstructor
public class ChangeLogTimelineService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ChangeLogRepository changeLogRepository;
    private final RoomMemberService roomMemberService;

    @Transactional(readOnly = true)
    public ChangeLogPageResponse getTimeline(
            Long roomId, Long userId, Long cursor, Long throughId, Integer requestedSize) {
        roomMemberService.getActiveMember(roomId, userId);
        int pageSize = requestedSize == null ? DEFAULT_PAGE_SIZE : requestedSize;
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        Slice<ChangeLog> page =
                changeLogRepository.findTimelinePage(
                        roomId, throughId, cursor, PageRequest.of(0, pageSize));
        List<ChangeLogItemResponse> items =
                page.getContent().stream().map(ChangeLogItemResponse::from).toList();
        Long nextCursor = page.hasNext() ? items.getLast().id() : null;
        return new ChangeLogPageResponse(items, nextCursor);
    }
}
