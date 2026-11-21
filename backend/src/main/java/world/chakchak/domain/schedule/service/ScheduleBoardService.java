package world.chakchak.domain.schedule.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.service.RoomMemberService;
import world.chakchak.domain.schedule.dto.DaySnapshot;
import world.chakchak.domain.schedule.dto.ScheduleBoardResponse;
import world.chakchak.domain.schedule.entity.ScheduleItem;
import world.chakchak.domain.schedule.repository.ScheduleDayRepository;
import world.chakchak.domain.schedule.repository.ScheduleItemRepository;

/** 일정 보드 기능. (B-01 보드 조회) */
@Service
@RequiredArgsConstructor
public class ScheduleBoardService {

    private final RoomMemberService roomMemberService;
    private final ScheduleDayRepository scheduleDayRepository;
    private final ScheduleItemRepository scheduleItemRepository;

    /**
     * 여행 첫날부터 마지막 날까지, 날마다 그날 일정 블록을 순서대로 담아서 돌려준다. 보드 화면에 들어올 때, 버전이 안 맞아 실패한 뒤, SSE가 다시 연결된 뒤에
     * 부른다. (B-01)
     *
     * <p>날이 며칠이든 블록이 몇 개든 쿼리 수는 같다 (멤버 확인, 날 목록, 블록 목록).
     *
     * @throws world.chakchak.global.error.BusinessException 방이 없으면 ROOM_NOT_FOUND, 활동 멤버가 아니면
     *     FORBIDDEN
     */
    @Transactional(readOnly = true)
    public ScheduleBoardResponse getBoard(Long roomId, Long userId) {
        RoomMember me = roomMemberService.getActiveMember(roomId, userId);

        // 블록을 날별로 나눈다. 순서값 순으로 가져왔으니 나눈 뒤에도 그 순서 그대로다
        Map<Long, List<ScheduleItem>> itemsByDay =
                scheduleItemRepository
                        .findAllByScheduleDay_Room_IdOrderBySortOrderAsc(roomId)
                        .stream()
                        .collect(Collectors.groupingBy(item -> item.getScheduleDay().getId()));

        List<DaySnapshot> days =
                scheduleDayRepository.findAllByRoom_IdOrderByDayIndexAsc(roomId).stream()
                        .map(day ->
                                DaySnapshot.of(
                                        day, itemsByDay.getOrDefault(day.getId(), List.of())))
                        .toList();
        return new ScheduleBoardResponse(me.getRoom().getStatus(), days);
    }
}
