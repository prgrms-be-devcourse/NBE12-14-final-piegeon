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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import world.chakchak.domain.agenda.errorcode.AgendaErrorCode;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.global.entity.BaseEntity;
import world.chakchak.global.error.BusinessException;

/**
 * 투표 안건. 설문 충돌(BUDGET/STAY/PACE)과 최종 확정(FINAL_PLAN) 모두 만장일치로 결정한다.
 *
 * <p>기한 안에 결정되지 않으면 EXPIRED가 되고, 설문 안건은 round를 1 올린 새 안건으로 다시 연다(V-04).
 */
@Entity
@Table(name = "agenda_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgendaItem extends BaseEntity {

    /** 다음 회차 안건의 투표 기간 */
    static final int NEXT_ROUND_VOTING_DAYS = 7;

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
    private int totalVoter;

    @Column(name = "deadline_at", nullable = false)
    private LocalDateTime deadlineAt;

    // TODO AgendaOption 엔티티를 만들 때 FK와 연관관계로 바꾼다 (안건↔선택지가 서로 참조하므로 FK는 ALTER로 추가)
    @Column(name = "resolved_option_id")
    private Long resolvedOptionId;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    private AgendaItem(
            Room room,
            AgendaSubject subject,
            int round,
            String title,
            int totalVoter,
            LocalDateTime deadlineAt) {
        this.room = room;
        this.subject = subject;
        this.round = round;
        this.title = title;
        this.status = AgendaStatus.VOTING;
        this.totalVoter = totalVoter;
        this.deadlineAt = deadlineAt;
    }

    /**
     * 투표 중(VOTING)인 안건을 연다. 만장일치 항목도 이렇게 연 뒤 선지를 만들고 바로 {@link #resolve}한다.
     *
     * @param title 안건 제목. service가 주제에 맞는 기본 제목을 정해서 넘긴다.
     */
    public static AgendaItem create(
            Room room,
            AgendaSubject subject,
            int round,
            String title,
            int totalVoter,
            LocalDateTime deadlineAt) {
        return new AgendaItem(room, subject, round, title, totalVoter, deadlineAt);
    }

    /** 마감 시각을 출발 전날 00:00(D-1)보다 늦지 않게 줄인다. */
    public static LocalDateTime capDeadline(LocalDateTime candidate, LocalDate startDate) {
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

    /** 선택지 하나로 결정한다. (7-1 만장일치, 최종 확정 반대, D-1 최종 집계) */
    public void resolve(Long optionId, LocalDateTime now) {
        requireVoting();
        this.status = AgendaStatus.RESOLVED;
        this.resolvedOptionId = optionId;
        this.resolvedAt = now;
    }

    /** 결정하지 못한 채 마감한다. (13-2 마감 스케줄러) */
    public void expire() {
        requireVoting();
        this.status = AgendaStatus.EXPIRED;
    }

    /** 투표 자격자가 방을 나갔을 때 부른다. 자격자가 0명이 되면 EXPIRED가 된다. (R-06) */
    public void removeVoter() {
        requireVoting();
        this.totalVoter--;
        if (totalVoter == 0) {
            this.status = AgendaStatus.EXPIRED;
        }
    }

    /**
     * 미결(EXPIRED)로 끝난 설문 안건의 다음 회차를 만든다. (V-04)
     *
     * <p>마감은 지금 + 7일이고 D-1보다 늦으면 D-1로 줄인다. D-1이 지났으면 열 수 없다. 이 안건이 가장 최근 회차인지는 DB를 봐야 하므로 service에서
     * 확인한다.
     */
    public AgendaItem openNextRound(int totalVoter, LocalDateTime now, LocalDate startDate) {
        if (!subject.isSurvey() || status != AgendaStatus.EXPIRED) {
            throw new BusinessException(AgendaErrorCode.AGENDA_NOT_REOPENABLE);
        }
        if (!now.isBefore(dayBeforeDeparture(startDate))) {
            throw new BusinessException(AgendaErrorCode.AGENDA_NOT_REOPENABLE);
        }
        LocalDateTime deadline = capDeadline(now.plusDays(NEXT_ROUND_VOTING_DAYS), startDate);
        // 제목은 직전 회차를 그대로 물려받는다
        return new AgendaItem(room, subject, round + 1, title, totalVoter, deadline);
    }

    private void requireVoting() {
        if (status != AgendaStatus.VOTING) {
            throw new BusinessException(AgendaErrorCode.AGENDA_CLOSED);
        }
    }
}
