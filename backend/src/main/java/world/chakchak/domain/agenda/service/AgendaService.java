package world.chakchak.domain.agenda.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.agenda.dto.AgendaDetailResponse;
import world.chakchak.domain.agenda.dto.AgendaDetailResponse.OptionResult;
import world.chakchak.domain.agenda.dto.AgendaListResponse;
import world.chakchak.domain.agenda.dto.MemberRef;
import world.chakchak.domain.agenda.dto.OptionRef;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.agenda.entity.AgendaOption;
import world.chakchak.domain.agenda.entity.AgendaStatus;
import world.chakchak.domain.agenda.entity.AgendaSubject;
import world.chakchak.domain.agenda.entity.Vote;
import world.chakchak.domain.agenda.errorcode.AgendaErrorCode;
import world.chakchak.domain.agenda.repository.AgendaItemRepository;
import world.chakchak.domain.agenda.repository.AgendaOptionRepository;
import world.chakchak.domain.agenda.repository.VoteRepository;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.service.RoomMemberService;
import world.chakchak.global.error.BusinessException;

/**
 * 안건 조회(V-01 목록, V-02 상세)와 7-1 만장일치 판정.
 *
 * <p>조회는 방 잠금을 잡지 않는다. 판정은 방 잠금을 잡은 호출자의 트랜잭션 안에서만 부를 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgendaService {

    private static final List<AgendaSubject> SURVEY_SUBJECTS =
            List.of(AgendaSubject.BUDGET, AgendaSubject.STAY, AgendaSubject.PACE);

    private final RoomMemberService roomMemberService;
    private final AgendaItemRepository agendaItemRepository;
    private final AgendaOptionRepository agendaOptionRepository;
    private final VoteRepository voteRepository;

    /** 방의 안건 전체 목록 (V-01). 필터 없이 모두 돌려주고 걸러 보기는 프론트가 한다. */
    public AgendaListResponse getAgendas(Long roomId, Long userId) {
        RoomMember me = roomMemberService.getActiveMember(roomId, userId);

        // 정렬(명세 V-01): 투표 중인 안건을 마감 임박 순으로 먼저, 끝난 안건을 최근 순으로 뒤에 붙인다
        List<AgendaItem> agendas = new ArrayList<>();
        agendas.addAll(
                agendaItemRepository.findAllByRoomIdAndStatusOrderByDeadlineAtAscIdAsc(
                        roomId, AgendaStatus.VOTING));
        agendas.addAll(
                agendaItemRepository.findAllByRoomIdAndStatusNotOrderByCreatedAtDescIdDesc(
                        roomId, AgendaStatus.VOTING));
        if (agendas.isEmpty()) {
            return new AgendaListResponse(List.of());
        }

        List<Long> agendaIds = agendas.stream().map(AgendaItem::getId).toList();
        Map<Long, List<Vote>> votesByAgenda =
                voteRepository.findAllByAgendaItemIdIn(agendaIds).stream()
                        .collect(Collectors.groupingBy(v -> v.getAgendaItem().getId()));

        Map<Long, OptionRef> resolvedOptions = resolvedOptions(agendas);

        List<AgendaListResponse.Item> summaries =
                agendas.stream()
                        .map(a -> {
                            List<Vote> votes = votesByAgenda.getOrDefault(a.getId(), List.of());
                            return AgendaListResponse.Item.of(
                                    a,
                                    votes.size(),
                                    a.isEligible(me),
                                    myOptionId(votes, me),
                                    // Map.of()는 get(null)에서 NPE를 던지므로 결정되지 않은 안건은 먼저 거른다
                                    a.getResolvedOptionId() == null
                                            ? null
                                            : resolvedOptions.get(a.getResolvedOptionId()));
                        })
                        .toList();
        return new AgendaListResponse(summaries);
    }

    /** 안건 상세와 선택지별 투표자 (V-02) */
    public AgendaDetailResponse getAgenda(Long roomId, Long agendaId, Long userId) {
        RoomMember me = roomMemberService.getActiveMember(roomId, userId);
        AgendaItem agenda =
                agendaItemRepository
                        .findByIdAndRoomId(agendaId, roomId)
                        .orElseThrow(() -> new BusinessException(AgendaErrorCode.AGENDA_NOT_FOUND));
        return toDetail(agenda, me);
    }

    /**
     * 7-1 만장일치 판정. 표가 들어왔거나(V-03) 자격자가 줄었을 때(R-06) 안건을 결정할 수 있는지 보고, 결정되면 방 상태까지 반영한다.
     *
     * <p>방 잠금({@code RoomService.getForUpdate})을 잡은 트랜잭션 안에서, 잠금으로 읽은 방을 넘겨 부른다. 안건 결정과 방 상태 변경이 같은
     * 트랜잭션에서 일어나야 "안건은 동의로 끝났는데 방은 REVIEWING" 같은 어긋남이 남지 않는다.
     *
     * @return 이번 호출로 안건이 결정됐으면 true
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean judge(Room lockedRoom, AgendaItem agenda, LocalDateTime now) {
        Optional<AgendaOption> decided =
                agenda.decidableOption(voteRepository.findAllByAgendaItemId(agenda.getId()));
        if (decided.isEmpty()) {
            return false;
        }
        AgendaOption option = decided.get();
        agenda.resolve(option, now);
        applyResolved(lockedRoom, agenda, option, now);
        return true;
    }

    /**
     * 안건이 미결(EXPIRED)로 끝난 뒤 방 상태를 반영한다. 최종 확정이면 방이 PLANNING으로 돌아간다. 설문 안건은 다음 회차(V-04)를 기다리므로 방은
     * SURVEYING에 머문다. (R-06 total_voter 0, 13-2 마감)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void applyExpired(Room lockedRoom, AgendaItem agenda) {
        if (agenda.getStatus() != AgendaStatus.EXPIRED) {
            throw new IllegalStateException("미결 마감된 안건이 아닙니다: agendaId=" + agenda.getId());
        }
        if (agenda.getSubject() == AgendaSubject.FINAL_PLAN) {
            lockedRoom.returnToPlanning();
        }
    }

    private void applyResolved(
            Room lockedRoom, AgendaItem agenda, AgendaOption option, LocalDateTime now) {
        if (agenda.getSubject() == AgendaSubject.FINAL_PLAN) {
            if (option.isReject()) {
                lockedRoom.returnToPlanning();
            } else {
                lockedRoom.finalizePlan(now);
            }
            return;
        }
        if (allSurveySubjectsResolved(lockedRoom.getId())) {
            lockedRoom.startPlanning();
        }
    }

    // 5-2: 예산·숙소·이동 강도의 가장 최근 회차가 모두 RESOLVED면 설문 조율이 끝난 것이다
    private boolean allSurveySubjectsResolved(Long roomId) {
        return SURVEY_SUBJECTS.stream()
                .allMatch(subject ->
                        agendaItemRepository
                                .findTopByRoomIdAndSubjectOrderByRoundDesc(roomId, subject)
                                .map(latest -> latest.getStatus() == AgendaStatus.RESOLVED)
                                .orElse(false));
    }

    /** 상세 응답 조립. V-03 투표, V-04 다음 회차의 응답에서도 쓴다. */
    private AgendaDetailResponse toDetail(AgendaItem agenda, RoomMember me) {
        List<AgendaOption> options =
                agendaOptionRepository.findAllByAgendaItemIdOrderByDisplayIndexAsc(agenda.getId());
        List<Vote> votes = voteRepository.findAllByAgendaItemId(agenda.getId());
        Map<Long, List<MemberRef>> votersByOption =
                votes.stream()
                        .collect(
                                Collectors.groupingBy(
                                        v -> v.getAgendaOption().getId(),
                                        Collectors.mapping(
                                                v -> MemberRef.from(v.getMember()),
                                                Collectors.toList())));

        List<OptionResult> results =
                options.stream()
                        .map(o ->
                                toOptionResult(
                                        o, votersByOption.getOrDefault(o.getId(), List.of())))
                        .toList();

        return AgendaDetailResponse.of(
                agenda, votes.size(), agenda.isEligible(me), myOptionId(votes, me), results);
    }

    private static OptionResult toOptionResult(AgendaOption option, List<MemberRef> voters) {
        MemberRef proposedBy =
                option.getProposedBy() == null ? null : MemberRef.from(option.getProposedBy());
        return new OptionResult(
                option.getId(),
                option.getLabel(),
                option.getValue(),
                voters.size(),
                voters,
                proposedBy);
    }

    /** 결정된 선지를 한 번에 읽는다. RESOLVED 안건이 없으면 조회하지 않는다. */
    private Map<Long, OptionRef> resolvedOptions(List<AgendaItem> agendas) {
        List<Long> ids =
                agendas.stream()
                        .map(AgendaItem::getResolvedOptionId)
                        .filter(Objects::nonNull)
                        .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return agendaOptionRepository.findAllById(ids).stream()
                .map(OptionRef::from)
                .collect(Collectors.toMap(OptionRef::optionId, Function.identity()));
    }

    private static Long myOptionId(List<Vote> votes, RoomMember me) {
        return votes.stream()
                .filter(v -> v.getMember().getId().equals(me.getId()))
                .map(v -> v.getAgendaOption().getId())
                .findFirst()
                .orElse(null);
    }
}
