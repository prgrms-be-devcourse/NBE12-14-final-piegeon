package world.chakchak.domain.agenda.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import world.chakchak.domain.agenda.errorcode.AgendaErrorCode;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.global.entity.BaseEntity;
import world.chakchak.global.error.BusinessException;

/**
 * 멤버 한 명이 안건 하나에 낸 표. 실명이고 멤버당 안건 하나에 1표다 (UNIQUE). 표를 바꾸면 UPDATE한다. (V-03)
 *
 * <p>투표 자격(joined_at <= 안건 생성 시각)과 최종 확정 반대 코멘트 필수 검사는 service가 한다.
 */
@Entity
@Table(name = "vote")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vote extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agenda_id", nullable = false)
    private AgendaItem agendaItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agenda_option_id", nullable = false)
    private AgendaOption agendaOption;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_room_member_id", nullable = false)
    private RoomMember member;

    private Vote(AgendaItem agendaItem, AgendaOption agendaOption, RoomMember member) {
        this.agendaItem = agendaItem;
        this.agendaOption = agendaOption;
        this.member = member;
    }

    /**
     * 표를 낸다.
     *
     * @throws BusinessException 안건이 투표 중이 아니거나 마감이 지났으면 AGENDA_CLOSED, 다른 안건의 선지면
     *     OPTION_NOT_IN_AGENDA
     */
    public static Vote create(
            AgendaItem agenda, AgendaOption option, RoomMember member, LocalDateTime now) {
        requireVotable(agenda, option, now);
        return new Vote(agenda, option, member);
    }

    /** 표를 다른 선지로 바꾼다. 검사는 create와 같고, 같은 선지면 아무것도 하지 않는다. (V-03 멱등) */
    public void change(AgendaOption option, LocalDateTime now) {
        requireVotable(agendaItem, option, now);
        if (isFor(option)) {
            return;
        }
        this.agendaOption = option;
    }

    private static void requireVotable(AgendaItem agenda, AgendaOption option, LocalDateTime now) {
        if (!agenda.isOpenAt(now)) {
            throw new BusinessException(AgendaErrorCode.AGENDA_CLOSED);
        }
        if (!option.belongsTo(agenda)) {
            throw new BusinessException(AgendaErrorCode.OPTION_NOT_IN_AGENDA);
        }
    }

    /** 이 선지에 낸 표인지. 7-1에서 모든 표가 같은 선지인지 셀 때 쓴다. */
    public boolean isFor(AgendaOption option) {
        // 프록시를 == 로 비교하면 같은 행이어도 false가 나와 id로 비교한다. 저장 전(id 없음)에는 객체 자체로 비교한다.
        Long id = option.getId();
        return agendaOption == option || (id != null && id.equals(agendaOption.getId()));
    }

    /** 최종 확정 반대 표인지. 반대 표가 들어오면 안건을 바로 끝낸다. (7-1) */
    public boolean isReject() {
        return agendaOption.isReject();
    }
}
