package world.chakchak.domain.comment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import world.chakchak.domain.agenda.entity.AgendaItem;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.global.entity.BaseEntity;

/**
 * 안건 또는 장소 후보에 붙는 코멘트. 둘 중 정확히 하나만 값이 있다 (DB CHECK).
 *
 * <p>안건 코멘트는 V-06과 최종 확정 반대 이유(V-03), 장소 코멘트는 P-09에서 만든다. 수정·삭제 API는 없다. 방 단계(FINALIZED면 작성 불가)와
 * 대상이 같은 방인지는 service가 확인한다.
 */
@Entity
@Table(name = "comment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseEntity {

    /** 본문 최대 길이. 요청 DTO의 @Size(max = 500)와 같다. */
    static final int BODY_MAX_LENGTH = 500;

    // 작성한 멤버
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_room_member_id", nullable = false)
    private RoomMember member;

    // 안건 코멘트면 값이 있고, 장소 코멘트면 null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agenda_id")
    private AgendaItem agendaItem;

    // 장소 코멘트면 값이 있고, 안건 코멘트면 null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_candidate_id")
    private PlaceCandidate placeCandidate;

    @Column(nullable = false, length = BODY_MAX_LENGTH)
    private String body;

    private Comment(
            RoomMember member, AgendaItem agendaItem, PlaceCandidate placeCandidate, String body) {
        // 요청 DTO(@NotBlank @Size(max = 500))가 먼저 막지만 한번 더
        if (body == null || body.isBlank() || body.length() > BODY_MAX_LENGTH) {
            throw new IllegalArgumentException("코멘트는 1~500자여야 합니다.");
        }
        this.member = member;
        this.agendaItem = agendaItem;
        this.placeCandidate = placeCandidate;
        this.body = body;
    }

    /** 안건 코멘트. (V-06, 최종 확정 반대 이유 V-03) */
    public static Comment onAgenda(AgendaItem agendaItem, RoomMember member, String body) {
        return new Comment(member, agendaItem, null, body);
    }

    /** 장소 후보 코멘트. (P-09) */
    public static Comment onPlace(PlaceCandidate placeCandidate, RoomMember member, String body) {
        return new Comment(member, null, placeCandidate, body);
    }
}
