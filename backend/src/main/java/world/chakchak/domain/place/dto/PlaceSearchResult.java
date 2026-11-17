package world.chakchak.domain.place.dto;

import java.util.List;

/** 장소 검색 한 페이지의 결과. (P-01) */
public record PlaceSearchResult(
        List<GooglePlace> places,
        // 다음 페이지 검색용 토큰. 마지막 페이지면 null
        String nextPageToken) {}
