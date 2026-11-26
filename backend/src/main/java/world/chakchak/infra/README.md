# infra — 외부 API 구현

외부 서비스를 실제로 부르는 코드를 둔다. 지금은 Google Places API (New) 하나다. 도메인은 인터페이스만 알고, HTTP 호출의 세부 사항은 이 패키지에 모은다.

## 주요 클래스

| 클래스 | 하는 일 |
| --- | --- |
| [`GooglePlacesClient`](google/GooglePlacesClient.java) | place의 `PlaceSearchClient` 구현체. 검색, 상세 조회, 사진 주소 조회 |
| [`GooglePlacesConfig`](google/GooglePlacesConfig.java) | Google을 부를 `RestClient`와 타임아웃 |
| [`GooglePlacesProperties`](google/GooglePlacesProperties.java) | `app.google-places` 설정 (주소, 타임아웃, API 키) |

## 알아 둘 규칙과 설계

### API 키는 서버에만 있다

키는 환경변수 `GOOGLE_PLACES_API_KEY`로만 받고, 요청 헤더(`X-Goog-Api-Key`)에 서버가 붙인다. 브라우저에는 노출되지 않는다. 운영 프로필에서 값이 비어 있으면 서버가 뜨지 않는다. 키를 빠뜨린 채 배포됐는데 장소 검색만 조용히 실패하는 일을 막으려는 것이다. 로컬 프로필은 키가 없는 팀원도 서버를 띄울 수 있게 가짜 기본값을 둔다. 이 값으로는 Google을 부르는 기능(검색, 후보 등록, 사진)만 실패한다.

### 받을 필드를 FieldMask로 줄인다

Google Places는 요청한 필드에 따라 요금이 달라진다. 검색(`SEARCH_FIELD_MASK`)은 목록에 필요한 id, 이름, 주소, 종류만 받는다. 지도 링크와 사진은 후보를 등록할 때 상세 조회(`DETAILS_FIELD_MASK`)에서만 받는다. 사진은 너비 800px로 줄인 주소를 받아 프론트를 그 주소로 302 리다이렉트한다.

### 3초가 지나면 끊는다

Google이 느려지면 우리 서버의 요청 스레드도 함께 묶인다. 그래서 `RestClient`에 3초 타임아웃을 걸고, 넘으면 `PLACES_API_ERROR`로 응답한다.

## 다른 패키지와의 관계

[place](../domain/place/README.md)의 `PlaceSearchClient` 인터페이스를 구현한다. place service는 이 패키지를 직접 알지 못하므로, 테스트에서는 Google 없이 가짜 구현을 넣을 수 있다.
