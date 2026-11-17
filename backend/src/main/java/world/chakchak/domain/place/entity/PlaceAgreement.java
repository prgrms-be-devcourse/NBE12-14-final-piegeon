package world.chakchak.domain.place.entity;

import jakarta.persistence.Entity;
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
 * 멤버 한 명이 장소 후보 하나에 동의한 기록. (테이블 place_agreement, V7)
 *
 * <p>후보를 등록한 사람은 자동으로 첫 동의를 남긴다. 한 멤버는 같은 후보에 한 번만 동의한다 (UNIQUE).
 */
@Entity
@Table(name = "place_agreement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceAgreement extends BaseEntity {

    // 동의한 장소 후보
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_candidate_id", nullable = false)
    private PlaceCandidate placeCandidate;

    // 동의한 멤버
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_room_member_id", nullable = false)
    private RoomMember member;

    private PlaceAgreement(PlaceCandidate placeCandidate, RoomMember member) {
        this.placeCandidate = placeCandidate;
        this.member = member;
    }

    public static PlaceAgreement create(PlaceCandidate placeCandidate, RoomMember member) {
        return new PlaceAgreement(placeCandidate, member);
    }
}
