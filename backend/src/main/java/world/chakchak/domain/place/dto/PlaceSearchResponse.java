package world.chakchak.domain.place.dto;

import java.util.List;

/** P-01 장소 검색 응답. Google 검색 결과 한 페이지와 다음 페이지 토큰. */
public record PlaceSearchResponse(
        List<PlaceSearchItem> places,
        // 다음 페이지 검색용 토큰. 마지막 페이지면 null
        String nextPageToken) {

    /** 검색 결과 장소 하나. */
    public record PlaceSearchItem(
            String googlePlaceId,
            String name,
            String address,
            String placeType,
            // 이 방에 이미 후보로 올라온 장소인지. true면 화면에서 "등록됨"으로 보여 준다
            boolean alreadyRegistered) {}
}
