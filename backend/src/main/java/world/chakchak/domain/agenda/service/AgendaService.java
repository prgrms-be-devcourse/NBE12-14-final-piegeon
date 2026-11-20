package world.chakchak.domain.agenda.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.domain.agenda.dto.AddOptionRequest;
import world.chakchak.domain.agenda.dto.AgendaDetailResponse;
import world.chakchak.domain.agenda.dto.AgendaDetailResponse.OptionResult;
import world.chakchak.domain.agenda.dto.AgendaListResponse;
import world.chakchak.domain.agenda.dto.MemberRef;
import world.chakchak.domain.agenda.dto.OptionRef;
import world.chakchak.domain.agenda.dto.VoteRequest;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.agenda.entity.AgendaOption;
import world.chakchak.domain.agenda.entity.AgendaStatus;
import world.chakchak.domain.agenda.entity.AgendaSubject;
import world.chakchak.domain.agenda.entity.Vote;
import world.chakchak.domain.agenda.errorcode.AgendaErrorCode;
import world.chakchak.domain.agenda.repository.AgendaItemRepository;
import world.chakchak.domain.agenda.repository.AgendaOptionRepository;
import world.chakchak.domain.agenda.repository.VoteRepository;
import world.chakchak.domain.comment.dto.CommentListResponse;
import world.chakchak.domain.comment.dto.CommentRequest;
import world.chakchak.domain.comment.dto.CommentResponse;
import world.chakchak.domain.comment.service.CommentService;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.service.RoomMemberService;
import world.chakchak.domain.room.service.RoomService;
import world.chakchak.domain.survey.entity.SurveyResponse;
import world.chakchak.domain.survey.service.SurveyResponseService;
import world.chakchak.global.error.BusinessException;
import world.chakchak.global.error.CommonErrorCode;

/**
 * 안건 조회(V-01 목록, V-02 상세), 투표(V-03), 안건 코멘트(V-05 목록, V-06 작성), 다음 회차 열기(V-04), 예산 선지 추가·삭제(V-07,
 * V-08), 7-1 만장일치 판정.
 *
 * <p>조회는 방 잠금을 잡지 않는다. 투표, 다음 회차 열기, 선지 추가·삭제는 방 잠금을 첫 쿼리로 잡는다. 판정은 방 잠금을 잡은 호출자의 트랜잭션 안에서만 부를 수
 * 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgendaService {

    private static final List<AgendaSubject> SURVEY_SUBJECTS =
            List.of(AgendaSubject.BUDGET, AgendaSubject.STAY, AgendaSubject.PACE);

    private final RoomService roomService;
    private final RoomMemberService roomMemberService;
    private final CommentService commentService;
    private final AgendaItemRepository agendaItemRepository;
    private final AgendaOptionRepository agendaOptionRepository;
    private final VoteRepository voteRepository;
    private final SurveyResponseService surveyResponseService;
    private final Clock clock;

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
        AgendaItem agenda = getAgendaInRoom(roomId, agendaId);
        return toDetail(agenda, me);
    }

    /** 안건 코멘트 목록 (V-05) */
    public CommentListResponse getComments(Long roomId, Long agendaId, Long userId) {
        roomMemberService.getActiveMember(roomId, userId);
        getAgendaInRoom(roomId, agendaId);
        return new CommentListResponse(commentService.getOnAgenda(agendaId));
    }

    /**
     * 안건 코멘트를 남긴다 (V-06). 안건이 이미 마감됐어도 남길 수 있다.
     *
     * <p>코멘트는 안건·방 상태를 바꾸지 않으므로 방 잠금을 잡지 않는다 (명세 V-06).
     */
    @Transactional
    public CommentResponse addComment(
            Long roomId, Long agendaId, Long userId, CommentRequest request) {
        RoomMember me = roomMemberService.getActiveMember(roomId, userId);
        // 명세 2-1: 코멘트는 확정 전 단계(SURVEYING·PLANNING·REVIEWING)에서만 받는다
        RoomStatus status = me.getRoom().getStatus();
        if (status != RoomStatus.SURVEYING
                && status != RoomStatus.PLANNING
                && status != RoomStatus.REVIEWING) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS);
        }
        AgendaItem agenda = getAgendaInRoom(roomId, agendaId);
        CommentResponse response =
                CommentResponse.from(commentService.addOnAgenda(agenda, me, request.body()));
        // TODO SSE (커밋 후): comment.created
        return response;
    }

    /**
     * 투표하거나 표를 바꾼다 (V-03). 표가 들어올 때마다 7-1로 판정해, 결정되면 방 상태까지 같은 트랜잭션에서 반영한다.
     *
     * <p>방 잠금을 첫 쿼리로 잡아 같은 방의 투표·퇴장·마감이 한 줄로 처리된다. 그래서 마지막 두 표가 동시에 들어와도 판정은 한 번만 일어난다.
     */
    @Transactional
    public AgendaDetailResponse vote(Long roomId, Long agendaId, Long userId, VoteRequest request) {
        Room room = roomService.getForUpdate(roomId);
        // 잠금을 기다린 뒤의 시각으로 마감을 판단하고 기록한다. 잠금 전에 구하면 기다린 사이에 지난 마감을 놓친다
        LocalDateTime now = LocalDateTime.now(clock);
        RoomMember me = roomMemberService.getActiveMember(roomId, userId);
        // 명세 2-1: 투표는 설문 안건을 정하는 SURVEYING과 최종 확정 투표 중인 REVIEWING에서만 받는다
        if (room.getStatus() != RoomStatus.SURVEYING && room.getStatus() != RoomStatus.REVIEWING) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS);
        }

        AgendaItem agenda = getAgendaInRoom(roomId, agendaId);
        if (!agenda.isOpenAt(now)) {
            throw new BusinessException(AgendaErrorCode.AGENDA_CLOSED);
        }
        if (!agenda.isEligible(me)) {
            throw new BusinessException(AgendaErrorCode.NOT_ELIGIBLE_VOTER);
        }
        AgendaOption option =
                agendaOptionRepository
                        .findByIdAndAgendaItemId(request.optionId(), agendaId)
                        .orElseThrow(
                                () -> new BusinessException(AgendaErrorCode.OPTION_NOT_IN_AGENDA));
        // 최종 확정은 "형식은 만장일치, 불만은 코멘트로". 반대할 때는 이유를 남겨야 한다
        boolean reject = option.isReject();
        if (reject && (request.comment() == null || request.comment().isBlank())) {
            throw new BusinessException(AgendaErrorCode.REJECT_COMMENT_REQUIRED);
        }

        voteRepository
                .findByAgendaItemIdAndMemberId(agendaId, me.getId())
                .ifPresentOrElse(
                        mine -> mine.change(option, now),
                        () -> voteRepository.save(Vote.create(agenda, option, me, now)));
        if (reject) {
            commentService.addOnAgenda(agenda, me, request.comment());
        }

        judge(room, agenda, now);
        // TODO change_log: 안건이 결정되면 AGENDA CONFIRM, 방 상태가 바뀌면 PLAN_ROOM UPDATE/CONFIRM (기록 방식이 정해지면
        // 추가)
        // TODO SSE (커밋 후): vote.updated, 결정되면 agenda.resolved, 방 상태가 바뀌면 room.status_changed

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

    /**
     * 설문이 마감되면 예산·숙소 타입·이동 강도의 1회차 안건을 연다 (5-1). 설문 마감을 판정한 쪽(S-02 마지막 제출, R-06 퇴장, 13-1 의견 마감
     * 스케줄러)이 방 잠금을 잡은 채 {@code Room.closeSurveyIfReady}가 true일 때 부른다.
     *
     * <ul>
     *   <li>선지는 마감 전에 낸 설문 답으로 만든다 (명세 6-1). 선지가 하나뿐이면 만장일치라 투표 없이 바로 결정한다.
     *   <li>세 항목이 모두 만장일치면 방을 바로 PLANNING으로 넘긴다 (5-2).
     *   <li>출발 전날 00:00(D-1)이 지났으면 안건을 열지 않는다. 퇴장·설문 제출 요청까지 실패시키지 않으려는 것이고, 그 방은 D-1 최종 집계(13-3)가
     *       처리한다.
     * </ul>
     *
     * <p>퇴장(R-06)에서는 {@link #memberLeft} 다음에 불러야 한다. 먼저 부르면 방금 연 안건에서 나간 멤버를 자격자로 보고 total_voter를 한
     * 번 더 줄인다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void openSurveyAgendas(Room lockedRoom, LocalDateTime now) {
        LocalDateTime closedAt = lockedRoom.getSurveyClosedAt();
        if (lockedRoom.getStatus() != RoomStatus.SURVEYING || closedAt == null) {
            throw new IllegalStateException("설문이 마감되지 않은 방입니다: roomId=" + lockedRoom.getId());
        }
        if (agendaItemRepository
                .findTopByRoomIdAndSubjectOrderByRoundDesc(lockedRoom.getId(), AgendaSubject.BUDGET)
                .isPresent()) {
            throw new IllegalStateException("이미 설문 안건이 열린 방입니다: roomId=" + lockedRoom.getId());
        }
        if (!AgendaItem.canOpenAt(lockedRoom, now)) {
            return;
        }

        // 명세 6-1: 판정은 마감 전에 낸 응답만으로 한다
        List<SurveyResponse> answers =
                surveyResponseService.getActiveMemberResponses(lockedRoom.getId()).stream()
                        .filter(answer -> !answer.getSubmittedAt().isAfter(closedAt))
                        .toList();
        int totalVoter = Math.toIntExact(roomMemberService.countActiveMembers(lockedRoom.getId()));

        boolean allResolved = true;
        for (AgendaSubject subject : SURVEY_SUBJECTS) {
            AgendaItem agenda =
                    agendaItemRepository.save(
                            AgendaItem.openFirstRound(lockedRoom, subject, totalVoter, now));
            List<AgendaOption> options = surveyOptions(agenda, answers);
            if (options.isEmpty()) {
                // 마감 조건이 "제출 1건 이상"이라 여기까지 오면 호출 버그다
                throw new IllegalStateException(
                        "설문 답이 없어 선지를 만들 수 없습니다: roomId=" + lockedRoom.getId());
            }
            agendaOptionRepository.saveAll(options);
            if (options.size() == 1) {
                agenda.resolveWithoutVote(options.getFirst(), now);
            } else {
                allResolved = false;
            }
        }
        if (allResolved) {
            lockedRoom.startPlanning();
        }
        // TODO change_log: SURVEY_CLOSED 한 줄 (만장일치 안건은 따로 남기지 않는다)
        // TODO SSE (커밋 후): survey.closed, 투표가 열린 안건마다 agenda.opened, PLANNING이 되면
        // room.status_changed
    }

    /** 설문 답에서 이 안건 주제의 값만 꺼내 선지를 만든다. 1회차라 직전 회차에서 옮길 선지는 없다. */
    private static List<AgendaOption> surveyOptions(
            AgendaItem agenda, List<SurveyResponse> answers) {
        return switch (agenda.getSubject()) {
            case BUDGET ->
                    AgendaOption.budgetOptions(
                            agenda,
                            answers.stream().map(SurveyResponse::getBudget).toList(),
                            List.of());
            case STAY ->
                    AgendaOption.stayOptions(
                            agenda, answers.stream().map(SurveyResponse::getStayType).toList());
            case PACE ->
                    AgendaOption.paceOptions(
                            agenda, answers.stream().map(SurveyResponse::getPace).toList());
            case FINAL_PLAN -> throw new IllegalStateException("최종 확정은 설문 안건이 아닙니다.");
        };
    }

    /** 활성 멤버가 퇴장한 후 진행 중인 안건의 참여 자격을 조정합니다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void memberLeft(Room lockedRoom, RoomMember member, LocalDateTime now) {
        boolean activeMembersRemain = roomMemberService.countActiveMembers(lockedRoom.getId()) > 0;
        List<AgendaItem> votingAgendas =
                agendaItemRepository.findAllByRoomIdAndStatus(
                        lockedRoom.getId(), AgendaStatus.VOTING);
        for (AgendaItem agenda : votingAgendas) {
            if (!agenda.isEligible(member)
                    || voteRepository
                            .findByAgendaItemIdAndMemberId(agenda.getId(), member.getId())
                            .isPresent()) {
                continue;
            }
            agenda.removeVoter();
            if (agenda.getStatus() == AgendaStatus.EXPIRED) {
                if (activeMembersRemain) {
                    applyExpired(lockedRoom, agenda);
                }
            } else if (activeMembersRemain) {
                judge(lockedRoom, agenda, now);
            }
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

    private AgendaItem getAgendaInRoom(Long roomId, Long agendaId) {
        return agendaItemRepository
                .findByIdAndRoomId(agendaId, roomId)
                .orElseThrow(() -> new BusinessException(AgendaErrorCode.AGENDA_NOT_FOUND));
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

    /**
     * 예산 안건에 내가 생각하는 금액을 선지로 추가한다 (V-07). 금액은 만원 단위로 반올림해 저장하고, 선지 목록의 맨 뒤에 붙인다.
     *
     * <p>선지가 늘어도 기존 표는 그대로이고 결정도 바뀌지 않으므로 7-1 판정은 하지 않는다. 같은 금액을 두 명이 동시에 올려도 방 잠금으로 한 명씩 처리되어 두
     * 번째는 OPTION_ALREADY_EXISTS가 된다.
     */
    @Transactional
    public AgendaDetailResponse addBudgetOption(
            Long roomId, Long agendaId, Long userId, AddOptionRequest request) {
        Room room = roomService.getForUpdate(roomId);
        LocalDateTime now = LocalDateTime.now(clock);
        RoomMember me = roomMemberService.getActiveMember(roomId, userId);
        // 명세 2-1: 예산 선지는 설문 조율 구간(SURVEYING)에서만 추가한다
        if (room.getStatus() != RoomStatus.SURVEYING) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS);
        }

        AgendaItem agenda = getAgendaInRoom(roomId, agendaId);
        if (agenda.getSubject() != AgendaSubject.BUDGET) {
            throw new BusinessException(AgendaErrorCode.OPTION_NOT_ADDABLE);
        }
        if (!agenda.isOpenAt(now)) {
            throw new BusinessException(AgendaErrorCode.AGENDA_CLOSED);
        }
        if (!agenda.isEligible(me)) {
            throw new BusinessException(AgendaErrorCode.NOT_ELIGIBLE_VOTER);
        }
        // 회차마다 1인 1개. 내 선지를 지우면(V-08) 다시 추가할 수 있다
        if (agendaOptionRepository.existsByAgendaItemIdAndProposedById(agendaId, me.getId())) {
            throw new BusinessException(AgendaErrorCode.OPTION_ADD_LIMIT_EXCEEDED);
        }
        // 반올림하면 다른 금액도 같은 선지가 되므로 반올림한 값으로 중복을 확인한다
        String value = String.valueOf(AgendaOption.roundBudget(request.amount()));
        agendaOptionRepository
                .findByAgendaItemIdAndValue(agendaId, value)
                .ifPresent(existing -> {
                    throw new BusinessException(
                            AgendaErrorCode.OPTION_ALREADY_EXISTS,
                            Map.of("optionId", existing.getId()));
                });

        int nextIndex =
                agendaOptionRepository
                                .findAllByAgendaItemIdOrderByDisplayIndexAsc(agendaId)
                                .stream()
                                .mapToInt(AgendaOption::getDisplayIndex)
                                .max()
                                .orElse(-1)
                        + 1;
        try {
            agendaOptionRepository.saveAndFlush(
                    AgendaOption.budget(agenda, request.amount(), nextIndex, me));
        } catch (DataIntegrityViolationException e) {
            // 방 잠금으로 먼저 막으므로 여기까지 오는 것은 UNIQUE(agenda_id, value)가 마지막으로 막은 경우다
            throw new BusinessException(AgendaErrorCode.OPTION_ALREADY_EXISTS);
        }
        // TODO SSE (커밋 후): agenda.option_added. change_log는 남기지 않는다 (명세 V-07)

        return toDetail(agenda, me);
    }

    /**
     * 내가 추가한 예산 선지를 지운다 (V-08). 그 선지에 있던 내 표도 함께 지운다.
     *
     * <p>다른 멤버의 표가 있으면 지우지 않는다. 지우면 그 사람의 표가 몰래 사라지기 때문이다. 표가 줄기만 하므로 이 삭제로 안건이 결정되지는 않아 7-1 판정은 하지
     * 않는다. 지운 뒤에는 이 회차에 다시 1개를 추가할 수 있다(V-07).
     */
    @Transactional
    public AgendaDetailResponse deleteBudgetOption(
            Long roomId, Long agendaId, Long optionId, Long userId) {
        Room room = roomService.getForUpdate(roomId);
        LocalDateTime now = LocalDateTime.now(clock);
        RoomMember me = roomMemberService.getActiveMember(roomId, userId);
        // 명세 2-1: 예산 선지는 설문 조율 구간(SURVEYING)에서만 지운다
        if (room.getStatus() != RoomStatus.SURVEYING) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS);
        }

        AgendaItem agenda = getAgendaInRoom(roomId, agendaId);
        if (!agenda.isOpenAt(now)) {
            throw new BusinessException(AgendaErrorCode.AGENDA_CLOSED);
        }
        AgendaOption option =
                agendaOptionRepository
                        .findByIdAndAgendaItemId(optionId, agendaId)
                        .orElseThrow(() -> new BusinessException(AgendaErrorCode.OPTION_NOT_FOUND));
        // 설문에서 나온 선지와 예산이 아닌 안건의 선지는 올린 사람이 없어 여기서 막힌다
        option.requireProposedBy(me);
        long othersVoteCount =
                voteRepository.countByAgendaOptionIdAndMemberIdNot(optionId, me.getId());
        if (othersVoteCount > 0) {
            throw new BusinessException(
                    AgendaErrorCode.OPTION_HAS_VOTES, Map.of("voteCount", othersVoteCount));
        }

        // 표가 선지를 참조하므로 내 표를 먼저 지운다
        voteRepository.deleteByAgendaOptionIdAndMemberId(optionId, me.getId());
        agendaOptionRepository.delete(option);
        // TODO SSE (커밋 후): agenda.option_deleted, 내 표가 있었으면 vote.updated

        return toDetail(agenda, me);
    }

    /**
     * 미결(EXPIRED)로 끝난 설문 안건의 다음 회차를 연다 (V-04). 자동으로 열리지 않고 멤버가 직접 연다.
     *
     * <p>투표 자격자는 지금 방에 남은 멤버 전원이다. 선지는 남은 멤버의 설문 답과 직전 회차에 남은 멤버가 추가한 예산 선지로 다시 만든다. 남은 멤버 중 이 항목에
     * 답한 사람이 없으면 직전 회차 선지를 그대로 옮긴다. 두 명이 동시에 눌러도 방 잠금으로 한 명씩 처리되어 두 번째는 AGENDA_ALREADY_REOPENED가
     * 된다.
     */
    @Transactional
    public AgendaDetailResponse openNextRound(Long roomId, Long agendaId, Long userId) {
        Room room = roomService.getForUpdate(roomId);
        LocalDateTime now = LocalDateTime.now(clock);
        RoomMember me = roomMemberService.getActiveMember(roomId, userId);
        // 명세 2-1: 다음 회차는 설문 조율 구간(SURVEYING)에서만 연다
        if (room.getStatus() != RoomStatus.SURVEYING) {
            throw new BusinessException(CommonErrorCode.INVALID_STATUS);
        }

        AgendaItem previous = getAgendaInRoom(roomId, agendaId);
        if (!previous.getSubject().isSurvey() || previous.getStatus() != AgendaStatus.EXPIRED) {
            throw new BusinessException(AgendaErrorCode.AGENDA_NOT_REOPENABLE);
        }
        AgendaItem latest =
                agendaItemRepository
                        .findTopByRoomIdAndSubjectOrderByRoundDesc(roomId, previous.getSubject())
                        .orElse(previous);
        if (!latest.getId().equals(previous.getId())) {
            throw new BusinessException(
                    AgendaErrorCode.AGENDA_ALREADY_REOPENED, Map.of("agendaId", latest.getId()));
        }

        AgendaItem next =
                previous.openNextRound(
                        Math.toIntExact(roomMemberService.countActiveMembers(roomId)), now);
        try {
            agendaItemRepository.saveAndFlush(next);
        } catch (DataIntegrityViolationException e) {
            // 방 잠금으로 먼저 막으므로 여기까지 오는 것은 UNIQUE(plan_room_id, subject, round)가 마지막으로 막은 경우다
            throw new BusinessException(AgendaErrorCode.AGENDA_ALREADY_REOPENED);
        }
        agendaOptionRepository.saveAll(nextRoundOptions(roomId, previous, next));
        // TODO change_log(AGENDA CREATE) + SSE (커밋 후): agenda.opened

        return toDetail(next, me);
    }

    private List<AgendaOption> nextRoundOptions(Long roomId, AgendaItem previous, AgendaItem next) {
        // 직전 회차에 남은 멤버가 추가한 예산 선지는 올린 사람을 유지해 옮긴다
        List<AgendaOption> carried =
                previous.getSubject() == AgendaSubject.BUDGET
                        ? agendaOptionRepository.findAllProposedByActiveMembers(previous.getId())
                        : List.of();
        // 설문 응답에서 이 주제의 값만 꺼내 넘긴다. 선지 규칙은 AgendaOption이 설문 엔티티를 모른 채 값으로만 정한다
        List<SurveyResponse> answers = surveyResponseService.getActiveMemberResponses(roomId);
        List<AgendaOption> built =
                switch (next.getSubject()) {
                    case BUDGET ->
                            AgendaOption.budgetOptions(
                                    next,
                                    answers.stream().map(SurveyResponse::getBudget).toList(),
                                    carried);
                    case STAY ->
                            AgendaOption.stayOptions(
                                    next,
                                    answers.stream().map(SurveyResponse::getStayType).toList());
                    case PACE ->
                            AgendaOption.paceOptions(
                                    next, answers.stream().map(SurveyResponse::getPace).toList());
                    case FINAL_PLAN -> throw new IllegalStateException("최종 확정 안건은 다음 회차로 열지 않습니다.");
                };
        if (!built.isEmpty()) {
            return built;
        }
        // 남은 멤버 중 이 항목에 답한 사람이 없다. 투표를 이어갈 수 있게 직전 회차 선지를 그대로 옮긴다
        List<AgendaOption> previousOptions =
                agendaOptionRepository.findAllByAgendaItemIdOrderByDisplayIndexAsc(
                        previous.getId());
        List<AgendaOption> copied = new ArrayList<>();
        previousOptions.forEach(option -> copied.add(option.copyTo(next, copied.size())));
        return copied;
    }
}
