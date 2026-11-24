package world.chakchak.domain.place.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.global.entity.BaseEntity;

/**
 * 방에 올라온 장소 후보 하나. (테이블 place_candidate, V6, V10)
 *
 * <p>투표 없이 자유롭게 등록하고, 칸반 칸은 WANT 반응(PlaceReaction) 수로 계산한다. confirmed_at이 있으면 확정된 장소다.
 */
@Entity
@Table(name = "place_candidate")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceCandidate extends BaseEntity {

    // 후보가 속한 방
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_room_id", nullable = false)
    private Room room;

    // 같은 방에 같은 장소는 한 번만 올라간다 (DB UNIQUE)
    @Column(name = "google_place_id", nullable = false)
    private String googlePlaceId;

    @Column(nullable = false)
    private String name;

    @Column(name = "place_type", length = 50)
    private String placeType;

    @Column private String address;

    // 장소 상세(P-10)의 "지도에서 보기" 링크. Google이 준 값
    @Column(name = "google_maps_url", length = 500)
    private String googleMapsUrl;

    // 사진 프록시(P-11)에서 사진 주소를 받을 때 쓰는 Google 사진 이름. 사진이 없으면 null
    @Column(name = "photo_name", length = 500)
    private String photoName;

    // 후보를 등록한 멤버
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "suggested_by", nullable = false)
    private RoomMember suggestedBy;

    // null이면 미확정
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    private PlaceCandidate(
            Room room,
            String googlePlaceId,
            String name,
            String placeType,
            String address,
            String googleMapsUrl,
            String photoName,
            RoomMember suggestedBy) {
        this.room = room;
        this.googlePlaceId = googlePlaceId;
        this.name = name;
        this.placeType = placeType;
        this.address = address;
        this.googleMapsUrl = googleMapsUrl;
        this.photoName = photoName;
        this.suggestedBy = suggestedBy;
    }

    public static PlaceCandidate create(
            Room room,
            String googlePlaceId,
            String name,
            String placeType,
            String address,
            String googleMapsUrl,
            String photoName,
            RoomMember suggestedBy) {
        return new PlaceCandidate(
                room,
                googlePlaceId,
                name,
                placeType,
                address,
                googleMapsUrl,
                photoName,
                suggestedBy);
    }

    public void confirm(Clock clock) {
        if (isConfirmed()) {
            return;
        }
        this.confirmedAt = LocalDateTime.now(clock);
    }

    public boolean isConfirmed() {
        return confirmedAt != null;
    }
}
