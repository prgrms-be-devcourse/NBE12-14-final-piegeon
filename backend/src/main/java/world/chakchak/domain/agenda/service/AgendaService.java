package world.chakchak.domain.agenda.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.agenda.dto.AgendaDetailResponse;
import world.chakchak.domain.agenda.dto.AgendaDetailResponse.OptionResult;
import world.chakchak.domain.agenda.dto.AgendaListResponse;
import world.chakchak.domain.agenda.dto.MemberRef;
import world.chakchak.domain.agenda.dto.OptionRef;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.agenda.entity.AgendaOption;
import world.chakchak.domain.agenda.entity.AgendaStatus;
import world.chakchak.domain.agenda.entity.Vote;
import world.chakchak.domain.agenda.errorcode.AgendaErrorCode;
import world.chakchak.domain.agenda.repository.AgendaItemRepository;
import world.chakchak.domain.agenda.repository.AgendaOptionRepository;
import world.chakchak.domain.agenda.repository.VoteRepository;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.service.RoomMemberService;
import world.chakchak.global.error.BusinessException;

/** 안건 조회 (V-01 목록, V-02 상세). 읽기만 하므로 방 잠금을 잡지 않는다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgendaService {

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
