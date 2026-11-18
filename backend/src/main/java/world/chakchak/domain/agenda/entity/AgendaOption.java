package world.chakchak.domain.agenda.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import world.chakchak.domain.agenda.errorcode.AgendaErrorCode;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.survey.entity.Pace;
import world.chakchak.domain.survey.entity.StayType;
import world.chakchak.global.entity.BaseEntity;
import world.chakchak.global.error.BusinessException;

/**
 * 안건의 선택지. value는 비교·집계용 값이고 label은 화면에 보여줄 글자다.
 *
 * <p>예) 예산 label "100만원" / value "1000000", 숙소 "전문 숙박시설(호텔·리조트)" / "HOTEL", 최종 확정 "동의" / "AGREE".
 * 안건 하나에 같은 value는 하나뿐이다 (UNIQUE). 선지는 주제별 팩토리로만 만들어 주제와 값의 종류가 어긋나지 않게 한다.
 */
@Entity
@Table(name = "agenda_option")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgendaOption extends BaseEntity {

    /** 예산 범위. S-01 limits, V-07 요청 검증과 같다. */
    static final long BUDGET_MIN = 1L;

    static final long BUDGET_MAX = 99_999_999L;

    /** 예산 선지는 만원 단위로 반올림한다. */
    private static final long MAN = 10_000L;

    /** 반올림한 예산의 범위. 1만원보다 작으면 1만원, 9,999만원을 넘으면 9,999만원으로 맞춘다. */
    static final long ROUNDED_BUDGET_MIN = MAN;

    static final long ROUNDED_BUDGET_MAX = BUDGET_MAX / MAN * MAN;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agenda_id", nullable = false)
    private AgendaItem agendaItem;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(nullable = false, length = 20)
    private String value;

    /** 화면에 보여줄 순서. 작을수록 앞이다. 순서는 service가 정한다. */
    @Column(name = "display_index", nullable = false)
    private int displayIndex;

    /** 예산 선지를 직접 추가한 멤버(V-07). 설문 응답에서 나온 선지와 최종 확정 선지는 null이다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposed_by")
    private RoomMember proposedBy;

    private AgendaOption(
            AgendaItem agendaItem,
            String label,
            String value,
            int displayIndex,
            RoomMember proposedBy) {
        if (displayIndex < 0) {
            throw new IllegalArgumentException("선지 순서는 0 이상이어야 합니다: " + displayIndex);
        }
        this.agendaItem = agendaItem;
        this.label = label;
        this.value = value;
        this.displayIndex = displayIndex;
        this.proposedBy = proposedBy;
    }

    /**
     * 예산 선지. 금액은 만원 단위로 반올림해 value에 저장하고, label은 그 금액으로 서버가 만든다 (1234567 → "1230000" / "123만원").
     *
     * <p>반올림하면 다른 금액이 같은 선지가 될 수 있다 (1226000, 1234000 → 123만원). service는 {@link #roundBudget}으로 먼저
     * 맞춘 뒤 중복을 거른다.
     *
     * @param amount 반올림 전 금액(원)
     * @param proposedBy 멤버가 직접 추가하면(V-07) 그 멤버, 설문 응답에서 나온 선지면 null
     * @throws BusinessException 예산 안건이 아니면 OPTION_NOT_ADDABLE
     */
    public static AgendaOption budget(
            AgendaItem agendaItem, long amount, int displayIndex, RoomMember proposedBy) {
        if (agendaItem.getSubject() != AgendaSubject.BUDGET) {
            throw new BusinessException(AgendaErrorCode.OPTION_NOT_ADDABLE);
        }
        // 요청 DTO와 설문에서 이미 막는 범위라, 여기까지 오면 코드 버그다
        if (amount < BUDGET_MIN || amount > BUDGET_MAX) {
            throw new IllegalArgumentException("예산 범위를 벗어났습니다: " + amount);
        }
        long rounded = roundBudget(amount);
        return new AgendaOption(
                agendaItem,
                String.format("%,d만원", rounded / MAN),
                String.valueOf(rounded),
                displayIndex,
                proposedBy);
    }

    /**
     * 만원 단위로 반올림한다. 4,999원 이하는 버리고 5,000원부터 올린다.
     *
     * <p>결과는 1만원 ~ 9,999만원이다. 5,000원 미만이 0원이 되거나 99,995,000원 이상이 1억이 되어 예산 범위를 벗어나는 것을 막는다.
     */
    public static long roundBudget(long amount) {
        long rounded = (amount + MAN / 2) / MAN * MAN;
        return Math.clamp(rounded, ROUNDED_BUDGET_MIN, ROUNDED_BUDGET_MAX);
    }

    /** 숙소 타입 선지. 설문 응답에서만 나온다. */
    public static AgendaOption stay(AgendaItem agendaItem, StayType stayType, int displayIndex) {
        requireSubject(agendaItem, AgendaSubject.STAY);
        return new AgendaOption(
                agendaItem, stayType.getLabel(), stayType.name(), displayIndex, null);
    }

    /** 이동 강도 선지. 설문 응답에서만 나온다. */
    public static AgendaOption pace(AgendaItem agendaItem, Pace pace, int displayIndex) {
        requireSubject(agendaItem, AgendaSubject.PACE);
        return new AgendaOption(agendaItem, pace.getLabel(), pace.name(), displayIndex, null);
    }

    /** 최종 확정 선지. 순서는 동의 → 반대로 고정이다. (C-01) */
    public static AgendaOption finalPlan(AgendaItem agendaItem, FinalPlanChoice choice) {
        requireSubject(agendaItem, AgendaSubject.FINAL_PLAN);
        return new AgendaOption(
                agendaItem, choice.getLabel(), choice.name(), choice.ordinal(), null);
    }

    private static void requireSubject(AgendaItem agendaItem, AgendaSubject expected) {
        if (agendaItem.getSubject() != expected) {
            throw new IllegalArgumentException(
                    expected + " 선지를 " + agendaItem.getSubject() + " 안건에 만들 수 없습니다.");
        }
    }

    /** 이 안건의 선지인지. 다른 안건 선지에 투표하거나(V-03) 결정하는 것을 막는다. */
    public boolean belongsTo(AgendaItem agenda) {
        return isSameEntity(agendaItem.getId(), agenda.getId()) || agendaItem == agenda;
    }

    /** 이 멤버가 직접 추가한 선지인지. 설문에서 나온 선지는 항상 false다. */
    public boolean isProposedBy(RoomMember member) {
        if (proposedBy == null) {
            return false;
        }
        return isSameEntity(proposedBy.getId(), member.getId()) || proposedBy == member;
    }

    /** 내가 추가한 선지만 지울 수 있다. 다른 사람의 표가 있는지는 service가 확인한다. (V-08) */
    public void requireProposedBy(RoomMember member) {
        if (!isProposedBy(member)) {
            throw new BusinessException(AgendaErrorCode.NOT_OPTION_OWNER);
        }
    }

    /** 예산 선지의 금액. D-1 최종 집계에서 동점이면 낮은 금액을 고를 때 쓴다. (13-3) */
    public long budgetAmount() {
        if (agendaItem.getSubject() != AgendaSubject.BUDGET) {
            throw new IllegalStateException("예산 선지가 아닙니다: " + value);
        }
        return Long.parseLong(value);
    }

    /** 최종 확정 반대 선지인지. 반대 표가 들어오면 안건을 바로 끝낸다. (7-1) */
    public boolean isReject() {
        return agendaItem.getSubject() == AgendaSubject.FINAL_PLAN
                && FinalPlanChoice.REJECT.name().equals(value);
    }

    // 프록시와 실제 객체를 == 로 비교하면 같은 행이어도 false가 나와 id로 비교한다. 저장 전(id 없음)에는 객체 자체로 비교한다.
    private static boolean isSameEntity(Long id, Long otherId) {
        return id != null && id.equals(otherId);
    }
}
