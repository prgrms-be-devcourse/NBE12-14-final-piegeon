package world.chakchak.domain.schedule.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.changelog.entity.ActionStatus;
import world.chakchak.domain.changelog.entity.EntityType;
import world.chakchak.domain.changelog.entity.EventKind;
import world.chakchak.domain.changelog.service.ChangeLogService;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.place.service.PlaceCandidateService;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.service.RoomMemberService;
import world.chakchak.domain.room.service.RoomService;
import world.chakchak.domain.schedule.dto.AddItemRequest;
import world.chakchak.domain.schedule.dto.AddItemResponse;
import world.chakchak.domain.schedule.dto.DayChangeResponse;
import world.chakchak.domain.schedule.dto.DaySnapshot;
import world.chakchak.domain.schedule.dto.MoveItemRequest;
import world.chakchak.domain.schedule.dto.ScheduleBoardResponse;
import world.chakchak.domain.schedule.dto.ScheduleVersionConflictData;
import world.chakchak.domain.schedule.entity.ScheduleDay;
import world.chakchak.domain.schedule.entity.ScheduleItem;
import world.chakchak.domain.schedule.errorcode.ScheduleErrorCode;
import world.chakchak.domain.schedule.repository.ScheduleDayRepository;
import world.chakchak.domain.schedule.repository.ScheduleItemRepository;
import world.chakchak.global.error.BusinessException;

/** 일정 보드 기능. (B-01 보드 조회, B-02 블록 추가, B-03 블록 이동) */
@Service
@RequiredArgsConstructor
public class ScheduleBoardService {

    // 하루에 넣을 수 있는 블록 수
    private static final int ITEMS_PER_DAY = 10;

    // 맨 뒤에 넣을 때, 그리고 다시 매길 때의 순서값 간격
    private static final int SORT_GAP = 1000;

    private final RoomService roomService;
    private final RoomMemberService roomMemberService;
    private final PlaceCandidateService placeCandidateService;
    private final ChangeLogService changeLogService;
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

    /**
     * 확정된 장소를 그날 원하는 자리에 블록으로 넣는다. (B-02)
     *
     * <p>방을 먼저 잠가서 같은 방의 쓰기 요청이 한 줄로 선다. 그래서 두 사람이 같은 자리에 넣어도 순서값이 겹치지 않는다.
     *
     * @throws BusinessException 방이 없으면 ROOM_NOT_FOUND, 활동 멤버가 아니면 FORBIDDEN, 방이 PLANNING이 아니면
     *     BOARD_LOCKED, 없거나 다른 방의 날이면 SCHEDULE_DAY_NOT_FOUND, 버전이 다르면 SCHEDULE_VERSION_CONFLICT,
     *     없거나 다른 방의 장소면 PLACE_CANDIDATE_NOT_FOUND, 그날 블록이 10개면 DAY_ITEM_LIMIT_EXCEEDED,
     *     afterItemId가 그날 블록이 아니면 SCHEDULE_ITEM_NOT_FOUND, 확정 안 된 장소면 PLACE_NOT_CONFIRMED
     */
    @Transactional
    public AddItemResponse addItem(Long roomId, Long dayId, Long userId, AddItemRequest request) {
        RoomMember me = lockBoard(roomId, userId);
        Room room = me.getRoom();
        ScheduleDay day = getDayInRoom(roomId, dayId);
        checkVersion(day, request.dayVersion());
        PlaceCandidate place = placeCandidateService.getCandidateInRoom(roomId, request.placeId());

        List<ScheduleItem> items =
                new ArrayList<>(
                        scheduleItemRepository.findAllByScheduleDay_IdOrderBySortOrderAsc(dayId));
        if (items.size() >= ITEMS_PER_DAY) {
            throw new BusinessException(ScheduleErrorCode.DAY_ITEM_LIMIT_EXCEEDED);
        }
        int position = positionAfter(items, request.afterItemId());
        int sortOrder = sortOrderAt(items, position);

        ScheduleItem item =
                scheduleItemRepository.save(
                        ScheduleItem.create(
                                day, place, sortOrder, request.startTime(), request.memo()));
        day.increaseVersion();
        items.add(position, item);

        // 예: "1일차 광장시장: 추가됐어요"
        changeLogService.record(
                room,
                me,
                EntityType.SCHEDULE_ITEM,
                item.getId(),
                EventKind.SCHEDULE_CHANGED,
                ActionStatus.CREATE,
                day.getDayIndex() + "일차 " + place.getName() + ": 추가됐어요");
        // TODO SSE (커밋 후): board.updated (바뀐 날의 스냅샷)
        return new AddItemResponse(item.getId(), DaySnapshot.of(day, items));
    }

    /**
     * 같은 날 안에서 블록을 afterItemId 블록 바로 뒤로 옮긴다. null이면 맨 앞. (B-03)
     *
     * @throws BusinessException 방이 없으면 ROOM_NOT_FOUND, 활동 멤버가 아니면 FORBIDDEN, 방이 PLANNING이 아니면
     *     BOARD_LOCKED, 없거나 다른 방의 블록이면 SCHEDULE_ITEM_NOT_FOUND, 버전이 다르면 SCHEDULE_VERSION_CONFLICT,
     *     afterItemId가 그날의 다른 블록이 아니면 SCHEDULE_ITEM_NOT_FOUND
     */
    @Transactional
    public DayChangeResponse moveItem(
            Long roomId, Long itemId, Long userId, MoveItemRequest request) {
        RoomMember me = lockBoard(roomId, userId);
        ScheduleItem item = getItemInRoom(roomId, itemId);
        ScheduleDay day = item.getScheduleDay();
        checkVersion(day, request.dayVersion());

        // 옮길 블록을 뺀 나머지에서 새 자리를 찾는다
        List<ScheduleItem> others =
                new ArrayList<>(
                        scheduleItemRepository.findAllByScheduleDay_IdOrderBySortOrderAsc(
                                day.getId()));
        others.remove(item);
        int position = positionAfter(others, request.afterItemId());

        // 나머지를 다시 매기면 1000, 2000...이 되는데, 옮길 블록의 옛 값과 겹치지 않게 아무도 안 쓰는 0으로 먼저 비켜 둔다
        item.changeSortOrder(0);
        item.changeSortOrder(sortOrderAt(others, position));
        day.increaseVersion();
        others.add(position, item);

        // 예: "1일차 광장시장: 순서가 바뀌었어요"
        changeLogService.record(
                me.getRoom(),
                me,
                EntityType.SCHEDULE_ITEM,
                item.getId(),
                EventKind.SCHEDULE_CHANGED,
                ActionStatus.UPDATE,
                day.getDayIndex() + "일차 " + item.getPlaceCandidate().getName() + ": 순서가 바뀌었어요");
        // TODO SSE (커밋 후): board.updated (바뀐 날의 스냅샷)
        return new DayChangeResponse(DaySnapshot.of(day, others), List.of());
    }

    // 보드 쓰기의 공통 시작. 방을 잠그고, 멤버인지, 보드를 고칠 수 있는 단계인지 본다
    private RoomMember lockBoard(Long roomId, Long userId) {
        Room room = roomService.getForUpdate(roomId);
        RoomMember me = roomMemberService.getActiveMember(roomId, userId);
        if (room.getStatus() != RoomStatus.PLANNING) {
            throw new BusinessException(ScheduleErrorCode.BOARD_LOCKED);
        }
        return me;
    }

    private ScheduleDay getDayInRoom(Long roomId, Long dayId) {
        return scheduleDayRepository
                .findByIdAndRoom_Id(dayId, roomId)
                .orElseThrow(() -> new BusinessException(ScheduleErrorCode.SCHEDULE_DAY_NOT_FOUND));
    }

    private ScheduleItem getItemInRoom(Long roomId, Long itemId) {
        return scheduleItemRepository
                .findByIdAndScheduleDay_Room_Id(itemId, roomId)
                .orElseThrow(
                        () -> new BusinessException(ScheduleErrorCode.SCHEDULE_ITEM_NOT_FOUND));
    }

    // 요청이 본 버전이 낡았으면, 프론트가 다시 불러올 수 있게 지금 버전을 같이 알려 준다
    private void checkVersion(ScheduleDay day, long dayVersion) {
        if (!day.isSameVersion(dayVersion)) {
            throw new BusinessException(
                    ScheduleErrorCode.SCHEDULE_VERSION_CONFLICT,
                    new ScheduleVersionConflictData(day.getId(), day.getVersion()));
        }
    }

    // 새 블록이 들어갈 자리(0이면 맨 앞). afterItemId 블록의 바로 다음 자리다
    private int positionAfter(List<ScheduleItem> items, Long afterItemId) {
        if (afterItemId == null) {
            return 0;
        }
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId().equals(afterItemId)) {
                return i + 1;
            }
        }
        throw new BusinessException(ScheduleErrorCode.SCHEDULE_ITEM_NOT_FOUND);
    }

    /**
     * items의 position 자리에 들어갈 순서값. 맨 뒤면 앞 + 1000, 그 밖에는 앞뒤의 가운데다. 맨 앞이면 앞을 0으로 본다.
     *
     * <p>가운데에 넣을 정수가 없으면(예: 1과 2 사이) 그날 블록을 1000 간격으로 다시 매긴 뒤 계산한다.
     */
    private int sortOrderAt(List<ScheduleItem> items, int position) {
        if (position == items.size()) {
            return items.isEmpty() ? SORT_GAP : items.get(position - 1).getSortOrder() + SORT_GAP;
        }
        int before = position == 0 ? 0 : items.get(position - 1).getSortOrder();
        int after = items.get(position).getSortOrder();
        if (after - before < 2) {
            renumber(items);
            return sortOrderAt(items, position);
        }
        return (before + after) / 2;
    }

    /**
     * 그날 블록의 순서값을 1000, 2000, 3000...으로 다시 매긴다. 순서는 그대로다.
     *
     * <p>같은 날 같은 순서값은 DB가 막는다. 바로 바꾸면 중간에 값이 겹칠 수 있어서, 먼저 겹칠 일 없는 음수로 바꿔 DB에 반영하고 그다음 진짜 값으로 바꾼다.
     */
    private void renumber(List<ScheduleItem> items) {
        for (int i = 0; i < items.size(); i++) {
            items.get(i).changeSortOrder(-(i + 1));
        }
        scheduleItemRepository.flush();
        for (int i = 0; i < items.size(); i++) {
            items.get(i).changeSortOrder((i + 1) * SORT_GAP);
        }
        scheduleItemRepository.flush();
    }
}
