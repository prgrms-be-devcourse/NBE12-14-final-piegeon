package world.chakchak.domain.room.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.changelog.entity.ChangeLog;
import world.chakchak.domain.changelog.entity.EventKind;
import world.chakchak.domain.changelog.repository.ChangeLogRepository;
import world.chakchak.domain.room.dto.SummaryResponse;
import world.chakchak.domain.room.dto.SummaryResponse.Kind;
import world.chakchak.domain.room.dto.SummaryResponse.SummaryGroup;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.errorcode.RoomErrorCode;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.survey.repository.SurveyResponseRepository;
import world.chakchak.global.error.BusinessException;
import world.chakchak.global.error.CommonErrorCode;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomSummaryService {

    private static final int MAX_LINES = 5;

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final ChangeLogRepository changeLogRepository;
    private final UnvotedAgendaProvider unvotedAgendaProvider;
    private final ActivePlaceFilterProvider activePlaceFilterProvider;
    private final Clock clock;

    public SummaryResponse getSummary(Long roomId, Long userId) {
        RoomRepository.RoomMemberAccessProjection access =
                roomRepository
                        .findRoomAndMemberForAccess(roomId, userId)
                        .orElseThrow(() -> new BusinessException(RoomErrorCode.ROOM_NOT_FOUND));
        RoomMember member = access.getActiveMember();
        if (member == null) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        LocalDateTime since =
                member.getLastSeenAt() == null ? member.getJoinedAt() : member.getLastSeenAt();
        LocalDateTime until = LocalDateTime.now(clock);
        List<ChangeLog> changes =
                changeLogRepository.findAfterSeenExcludingActor(roomId, member.getId(), since);
        List<SummaryGroup> groups = summarize(changes);
        boolean surveyNotSubmitted =
                surveyResponseRepository.findByRoomMember_Id(member.getId()).isEmpty();
        return new SummaryResponse(
                since,
                until,
                changes.size(),
                groups,
                new SummaryResponse.NeedsMyAction(
                        surveyNotSubmitted,
                        unvotedAgendaProvider.findUnvotedAgendas(roomId, member.getId())));
    }

    private List<SummaryGroup> summarize(List<ChangeLog> changes) {
        Map<EventKind, List<String>> linesByKind = new LinkedHashMap<>();
        Map<EventKind, Long> counts = new EnumMap<>(EventKind.class);
        List<ChangeLog> registrations =
                changes.stream()
                        .filter(change -> change.getEventKind() == EventKind.PLACE_REGISTERED)
                        .toList();
        List<ChangeLog> validRegistrations =
                activePlaceFilterProvider.filterActivePlaces(registrations);

        for (ChangeLog change : changes) {
            if (change.getEventKind() == EventKind.PLACE_REGISTERED
                    && !validRegistrations.contains(change)) {
                continue;
            }
            EventKind kind = change.getEventKind();
            counts.merge(kind, 1L, Long::sum);
            List<String> lines = linesByKind.computeIfAbsent(kind, ignored -> new ArrayList<>());
            if (lines.size() < MAX_LINES) {
                lines.add(
                        kind == EventKind.SCHEDULE_CHANGED
                                ? change.getCreatedAt().toLocalDate().toString()
                                : change.getSummary());
            }
        }

        // 보드 변경은 개별 문구 대신 변경이 있었던 일차별 건수로 요약한다.
        if (counts.containsKey(EventKind.SCHEDULE_CHANGED)) {
            Map<LocalDate, Long> perDay = new LinkedHashMap<>();
            changes.stream()
                    .filter(change -> change.getEventKind() == EventKind.SCHEDULE_CHANGED)
                    .forEach(change ->
                            perDay.merge(change.getCreatedAt().toLocalDate(), 1L, Long::sum));
            List<String> scheduleLines =
                    perDay.entrySet().stream()
                            .limit(MAX_LINES)
                            .map(entry -> entry.getKey() + " 일정 " + entry.getValue() + "건 변경")
                            .toList();
            linesByKind.put(EventKind.SCHEDULE_CHANGED, scheduleLines);
        }

        return linesByKind.entrySet().stream()
                .map(entry ->
                        new SummaryGroup(
                                Kind.valueOf(entry.getKey().name()),
                                counts.get(entry.getKey()),
                                List.copyOf(entry.getValue())))
                .toList();
    }
}
