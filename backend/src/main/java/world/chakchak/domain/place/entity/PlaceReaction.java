package world.chakchak.domain.place.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.global.entity.BaseEntity;

/**
 * 멤버 한 명이 장소 후보 하나에 남긴 반응(가고 싶어요 / 별로예요). (테이블 place_reaction, V11)
 *
 * <p>멤버당 후보 하나에 반응 1개다 (UNIQUE). 바꾸면 UPDATE, 취소하면 DELETE한다. 후보를 등록한 사람은 자동으로 WANT를 남긴다. 칸반 단계는 WANT
 * 수로 계산한다.
 */
@Entity
@Table(name = "place_reaction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceReaction extends BaseEntity {

    // 반응한 장소 후보
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_candidate_id", nullable = false)
    private PlaceCandidate placeCandidate;

    // 반응한 멤버
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_room_member_id", nullable = false)
    private RoomMember member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PlaceReactionType reaction;

    private PlaceReaction(
            PlaceCandidate placeCandidate, RoomMember member, PlaceReactionType reaction) {
        this.placeCandidate = placeCandidate;
        this.member = member;
        this.reaction = reaction;
    }

    public static PlaceReaction create(
            PlaceCandidate placeCandidate, RoomMember member, PlaceReactionType reaction) {
        return new PlaceReaction(placeCandidate, member, reaction);
    }

    /** 반응을 바꾼다. 같은 반응이면 아무것도 하지 않는다. (P-04 멱등) */
    public void change(PlaceReactionType reaction) {
        this.reaction = reaction;
    }

    public boolean isWant() {
        return reaction == PlaceReactionType.WANT;
    }
}
