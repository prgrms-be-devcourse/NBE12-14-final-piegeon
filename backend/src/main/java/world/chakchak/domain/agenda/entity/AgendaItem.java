package world.chakchak.domain.agenda.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import world.chakchak.domain.agenda.errorcode.AgendaErrorCode;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.global.entity.BaseEntity;
import world.chakchak.global.error.BusinessException;

/**
 * 투표 안건. 설문 충돌(BUDGET/STAY/PACE), 최종 확정(FINAL_PLAN), 자유 안건(CUSTOM) 모두 만장일치로 결정한다.
 *
 * <p>기한 안에 결정되지 않으면 EXPIRED가 되고, 설문 안건과 자유 안건은 round를 1 올린 새 안건으로 다시 연다(V-04).
 */
@Entity
@Table(name = "agenda_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgendaItem extends BaseEntity {

    /** 설문 마감 때 여는 1회차 안건의 투표 기간 (명세 5-1) */
    static final int FIRST_ROUND_VOTING_DAYS = 4;

    /** 다음 회차(V-04)와 최종 확정(C-01) 안건의 투표 기간. 마감은 D-1을 넘지 않게 줄인다. */
    static final int VOTING_DAYS = 7;

    static final String FINAL_REVIEW_TITLE = "이 계획으로 확정할까요?";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_room_id", nullable = false)
    private Room room;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgendaSubject subject;

    @Column(nullable = false)
    private int round;

    @Column(nullable = false, length = 50)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgendaStatus status;

    /** 투표 자격자 수. 자격자가 나가면 1씩 줄어든다. */
    @Column(name = "total_voter", nullable = false)
    private long totalVoter;

    @Column(name = "deadline_at", nullable = false)
    private LocalDateTime deadlineAt;

    // 결정된 선택지 id. 안건↔선택지가 서로 참조하므로 FK는 V13에서 ALTER로 걸었다 (이 안건의 선지만 가리킬 수 있음)
    @Column(name = "resolved_option_id")
    private Long resolvedOptionId;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    private AgendaItem(
            Room room,
            AgendaSubject subject,
            int round,
            String title,
            long totalVoter,
            LocalDateTime now,
            LocalDateTime deadlineAt) {
        // D-1이 됐거나 지났으면 안건을 만들지 않는다. 만들면 마감이 D-1로 줄어 만들자마자 투표할 수 없는 안건이 된다.
        // (예: 출발 4일 전에 만든 방은 설문 마감(생성 + 3일)이 정확히 D-1이다)
        if (!canOpenAt(room, now)) {
            throw new BusinessException(AgendaErrorCode.AGENDA_DEADLINE_PASSED);
        }
        // 투표자 0명으로 안건을 열면 아무도 투표할 수 없어 마감까지 VOTING으로 남는다.
        if (totalVoter < 1) {
            throw new IllegalArgumentException("투표자 수는 1명 이상이어야 합니다: " + totalVoter);
        }
        this.room = room;
        this.subject = subject;
        this.round = round;
        this.title = title;
        this.status = AgendaStatus.VOTING;
        this.totalVoter = totalVoter;
        // 마감 시각은 항상 D-1(출발 전날 00:00)을 넘지 않게 엔티티에서 줄인다.
        this.deadlineAt = capDeadline(deadlineAt, room.getStartDate());
    }

    /**
     * 투표 중(VOTING)인 안건을 연다. 만장일치 항목도 이렇게 연 뒤 선지를 만들고 바로 {@link #resolve}한다.
     *
     * @param title 안건 제목. service가 주제에 맞는 기본 제목을 정해서 넘긴다.
     * @param totalVoter 투표 자격자 수. 1 이상이어야 한다.
     * @param now 지금 시각. D-1 이후면 AGENDA_DEADLINE_PASSED를 던진다.
     * @param deadlineAt 마감 후보 시각. D-1보다 늦으면 D-1로 줄여서 저장한다. 만장일치 안건은 now를 넘긴다.
     */
    public static AgendaItem create(
            Room room,
            AgendaSubject subject,
            int round,
            String title,
            long totalVoter,
            LocalDateTime now,
            LocalDateTime deadlineAt) {
        return new AgendaItem(room, subject, round, title, totalVoter, now, deadlineAt);
    }

    /**
     * 설문 마감 때 설문 항목의 1회차 안건을 연다. (5-1)
     *
     * <p>마감은 설문 마감 + 4일이고 D-1보다 늦으면 D-1로 줄인다. 만장일치 항목도 이렇게 연 뒤 선지를 만들고 {@link #resolveWithoutVote}로
     * 바로 결정한다.
     *
     * @param surveyClosedAt 설문 마감 시각. 안건을 여는 지금 시각이기도 하다
     */
    public static AgendaItem openFirstRound(
            Room room, AgendaSubject subject, long totalVoter, LocalDateTime surveyClosedAt) {
        return create(
                room,
                subject,
                1,
                firstRoundTitle(subject),
                totalVoter,
                surveyClosedAt,
                surveyClosedAt.plusDays(FIRST_ROUND_VOTING_DAYS));
    }

    private static String firstRoundTitle(AgendaSubject subject) {
        return switch (subject) {
            case BUDGET -> "예산을 정해주세요";
            case STAY -> "숙소 타입을 정해주세요";
            case PACE -> "이동 강도를 정해주세요";
            case FINAL_PLAN, CUSTOM ->
                    throw new IllegalArgumentException(subject + " 안건은 설문 마감 때 열지 않습니다.");
        };
    }

    /**
     * 지금 이 방에 안건을 열 수 있는지. 출발 전날 00:00(D-1)이 됐거나 지났으면 열 수 없다.
     *
     * <p>설문 마감(5-1)처럼 다른 요청(퇴장, 설문 제출) 안에서 안건을 열 때, 409로 그 요청까지 실패시키지 않고 건너뛰려고 미리 확인한다.
     */
    public static boolean canOpenAt(Room room, LocalDateTime now) {
        return now.isBefore(dayBeforeDeparture(room.getStartDate()));
    }

    /**
     * 마감 시각을 출발 전날 00:00(D-1)보다 늦지 않게 줄인다.
     *
     * <p>생성자에서 항상 적용하므로 외부에서 부를 일이 없어 private으로 바꿨다.
     */
    private static LocalDateTime capDeadline(LocalDateTime candidate, LocalDate startDate) {
        LocalDateTime dayBeforeDeparture = dayBeforeDeparture(startDate);
        return candidate.isAfter(dayBeforeDeparture) ? dayBeforeDeparture : candidate;
    }

    // D-1을 알려주는 날짜 메서드
    private static LocalDateTime dayBeforeDeparture(LocalDate startDate) {
        return startDate.minusDays(1).atStartOfDay();
    }

    /** 지금 투표·선지 추가·삭제를 받을 수 있는지. VOTING이고 마감 전이어야 한다. */
    public boolean isOpenAt(LocalDateTime now) {
        return status == AgendaStatus.VOTING && now.isBefore(deadlineAt);
    }

    /**
     * 마감 시각이 지나 미결로 끝낼 안건인지. (13-2)
     *
     * <p>설문 안건과 최종 확정은 D-1부터 최종 집계가 낸 표로 결정하므로 대상이 아니다. 자유 안건은 최종 집계가 없어 D-1 이후에도 마감이 지나면 미결로 끝낸다.
     */
    public boolean isExpirableAt(LocalDateTime now) {
        return status == AgendaStatus.VOTING
                && !now.isBefore(deadlineAt)
                && (subject == AgendaSubject.CUSTOM || canOpenAt(room, now));
    }

    /**
     * 이 멤버에게 투표 자격이 있는지. 안건이 열린 시각(created_at) 이전에 합류한 멤버만 자격이 있다. (V-01·V-02 eligible, V-03)
     *
     * <p>방을 나갔는지는 보지 않는다. service가 getActiveMember로 먼저 확인한다.
     */
    public boolean isEligible(RoomMember member) {
        if (getCreatedAt() == null) {
            throw new IllegalStateException("저장되지 않은 안건은 투표 자격을 판단할 수 없습니다.");
        }
        return !member.getJoinedAt().isAfter(getCreatedAt());
    }

    /** 선택지 하나로 결정한다. (7-1 만장일치, 최종 확정 반대, D-1 최종 집계) */
    // 선지 객체를 받아 이 안건의 선지인지 먼저 확인한다. id만 받으면 다른 안건의 선지여도 flush 때 V13 FK에 걸려 500이 된다.
    // 저장은 id로 한다. 안건과 선지가 서로를 객체로 참조하지 않게 하기 위해서다.
    public void resolve(AgendaOption option, LocalDateTime now) {
        requireVoting();
        markResolved(option, now);
    }

    /** D-1 최종 집계로 결정한다. 미결(EXPIRED)로 끝나고 다음 회차가 열리지 않은 안건도 이 회차의 표로 결정한다. */
    public void resolveByFinalTally(AgendaOption option, LocalDateTime now) {
        if (status == AgendaStatus.RESOLVED) {
            throw new BusinessException(AgendaErrorCode.AGENDA_CLOSED);
        }
        markResolved(option, now);
    }

    private void markResolved(AgendaOption option, LocalDateTime now) {
        if (!option.belongsTo(this)) {
            throw new IllegalArgumentException("이 안건의 선택지가 아닙니다: " + option.getId());
        }
        // FK가 있어 저장된 선지만 결정할 수 있다
        if (option.getId() == null) {
            throw new IllegalStateException("저장되지 않은 선택지로는 결정할 수 없습니다.");
        }
        this.status = AgendaStatus.RESOLVED;
        this.resolvedOptionId = option.getId();
        this.resolvedAt = now;
    }

    /** 투표 없이 바로 결정한다. 설문 답이 모두 같아 선지가 하나뿐인 1회차 안건에 쓴다. 마감도 지금으로 맞춘다. (5-1 만장일치 항목) */
    public void resolveWithoutVote(AgendaOption option, LocalDateTime now) {
        requireVoting();
        this.deadlineAt = now;
        resolve(option, now);
    }

    /**
     * 표가 들어오거나 자격자가 줄었을 때 지금 결정할 선지를 고른다. 아직 결정할 수 없으면 비어 있다. (7-1)
     *
     * <ul>
     *   <li>최종 확정에 반대 표가 하나라도 있으면 그 반대 선지. 만장일치라 더 기다려도 통과할 수 없다.
     *   <li>표 수가 total_voter에 닿았고 모든 표가 같은 선지면 그 선지.
     * </ul>
     *
     * <p>마감 시각의 판정(13-2)과 D-1 최종 집계(13-3)는 규칙이 달라 여기서 다루지 않는다.
     *
     * @param votes 이 안건에 들어온 표 전부
     */
    public Optional<AgendaOption> decidableOption(List<Vote> votes) {
        if (status != AgendaStatus.VOTING) {
            return Optional.empty();
        }
        if (subject == AgendaSubject.FINAL_PLAN) {
            Optional<AgendaOption> reject =
                    votes.stream().filter(Vote::isReject).map(Vote::getAgendaOption).findFirst();
            if (reject.isPresent()) {
                return reject;
            }
        }
        if (votes.size() < totalVoter) {
            return Optional.empty();
        }
        AgendaOption first = votes.getFirst().getAgendaOption();
        boolean unanimous = votes.stream().allMatch(v -> v.isFor(first));
        return unanimous ? Optional.of(first) : Optional.empty();
    }

    /**
     * D-1 최종 집계로 정할 선지를 고른다. 정할 수 없으면 비어 있고, 그 항목은 미결로 끝난다.
     *
     * <ul>
     *   <li>표가 없으면 정할 수 없다.
     *   <li>최종 확정은 반대 표가 있으면 반대, 없으면 동의다.
     *   <li>설문 안건은 단독 최다 득표 선지다. 1위가 동점이면 정할 수 없다.
     * </ul>
     *
     * @param votes 집계할 표. 나간 멤버의 표는 service가 미리 뺀다
     */
    public Optional<AgendaOption> finalTallyOption(List<Vote> votes) {
        if (votes.isEmpty()) {
            return Optional.empty();
        }
        if (subject == AgendaSubject.FINAL_PLAN) {
            return votes.stream()
                    .filter(Vote::isReject)
                    .map(Vote::getAgendaOption)
                    .findFirst()
                    .or(() -> Optional.of(votes.getFirst().getAgendaOption()));
        }
        Map<Long, List<Vote>> votesByOption =
                votes.stream().collect(Collectors.groupingBy(v -> v.getAgendaOption().getId()));
        int topCount = votesByOption.values().stream().mapToInt(List::size).max().orElseThrow();
        List<AgendaOption> topOptions =
                votesByOption.values().stream()
                        .filter(optionVotes -> optionVotes.size() == topCount)
                        .map(optionVotes -> optionVotes.getFirst().getAgendaOption())
                        .toList();
        return topOptions.size() == 1 ? Optional.of(topOptions.getFirst()) : Optional.empty();
    }

    /** 결정하지 못한 채 마감한다. (13-2 마감 스케줄러) */
    public void expire() {
        requireVoting();
        this.status = AgendaStatus.EXPIRED;
    }

    /**
     * 최종 확정(FINAL_PLAN) 안건을 연다. (C-01)
     *
     * <p>마감은 지금 + 7일이고 D-1보다 늦으면 D-1로 줄인다. D-1이 지났으면 AGENDA_DEADLINE_PASSED를 던진다. 회차는 반대·미결로 끝난 뒤
     * 다시 열 때마다 1씩 오르며, 이전 회차를 읽어야 하므로 service가 정한다.
     */
    public static AgendaItem openFinalReview(
            Room room, int round, long totalVoter, LocalDateTime now) {
        return create(
                room,
                AgendaSubject.FINAL_PLAN,
                round,
                FINAL_REVIEW_TITLE,
                totalVoter,
                now,
                now.plusDays(VOTING_DAYS));
    }

    /**
     * 멤버가 직접 주제와 선지를 정한 자유 안건의 1회차를 연다. (V-09)
     *
     * <p>마감은 지금 + 7일이고 D-1보다 늦으면 D-1로 줄인다. D-1이 지났으면 AGENDA_DEADLINE_PASSED를 던진다. 같은 제목의 안건이 이미
     * 있는지는 DB를 봐야 하므로 service에서 확인한다.
     *
     * @param title 주제 이름. 같은 방의 자유 안건끼리 겹치지 않고, 열린 뒤에는 바꾸지 않는다
     */
    public static AgendaItem openCustom(
            Room room, String title, long totalVoter, LocalDateTime now) {
        return create(
                room, AgendaSubject.CUSTOM, 1, title, totalVoter, now, now.plusDays(VOTING_DAYS));
    }

    /**
     * 투표 자격자가 방을 나갔을 때 부른다. 자격자가 0명이 되면 EXPIRED가 된다. (R-06)
     *
     * <p>VOTING 안건에만 부른다. 결정·마감된 안건에 부르는 것은 코드 버그라서 조용히 넘기지 않고 AGENDA_CLOSED를 던진다. R-06 service는
     * {@code AgendaItemRepository.findAllByRoomIdAndStatus(roomId, VOTING)}로 투표 중인 안건만 가져오고, 그중 나가는
     * 멤버에게 투표 자격이 있던 안건(joined_at <= 안건 생성 시각)에만 호출한다.
     */
    public void removeVoter() {
        requireVoting();
        this.totalVoter--;
        if (totalVoter == 0) {
            this.status = AgendaStatus.EXPIRED;
        }
    }

    /**
     * 미결(EXPIRED)로 끝난 설문 안건이나 자유 안건의 다음 회차를 만든다. (V-04)
     *
     * <p>마감은 지금 + 7일이고 D-1보다 늦으면 D-1로 줄인다. D-1이 지났으면 열 수 없다. 이 안건이 가장 최근 회차인지는 DB를 봐야 하므로 service에서
     * 확인한다.
     */
    // 출발일은 room에서 직접 꺼내므로 startDate 인자를 없앴다. service가 다른 날짜를 넘길 여지가 사라진다.
    public AgendaItem openNextRound(long totalVoter, LocalDateTime now) {
        if (!subject.isReopenable() || status != AgendaStatus.EXPIRED) {
            throw new BusinessException(AgendaErrorCode.AGENDA_NOT_REOPENABLE);
        }
        // 다음 회차도 create로 만들어 D-1 이후 생성 금지와 D-1 상한을 같은 규칙으로 적용한다.
        // 제목은 직전 회차를 그대로 물려받는다
        return create(room, subject, round + 1, title, totalVoter, now, now.plusDays(VOTING_DAYS));
    }

    private void requireVoting() {
        if (status != AgendaStatus.VOTING) {
            throw new BusinessException(AgendaErrorCode.AGENDA_CLOSED);
        }
    }
}
