package world.chakchak.domain.place.dto;

/**
 * Google Places에서 받아 온 장소 하나. PlaceSearchClient가 service에 넘겨주는 내부 전달용 값이다.
 *
 * <p>API 요청·응답 DTO가 아니라서 Request/Response 접미사를 붙이지 않는다. 클라이언트에 내보낼 때는 따로 XxxResponse를 만든다.
 */
public record GooglePlace(
        String googlePlaceId,
        // 장소 이름 (Google displayName.text)
        String name,
        // 주소 (Google formattedAddress)
        String address,
        // 장소 종류 (Google primaryType, 예: market)
        String placeType,
        // 구글 지도 링크 (Google googleMapsUri). 상세 조회에서만 받고, 검색 결과에서는 null
        String googleMapsUrl,
        // 첫 번째 사진 이름 (Google photos[0].name). 사진이 없거나 검색 결과면 null
        String photoName) {}
