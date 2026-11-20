package world.chakchak.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.place.dto.GooglePlace;
import world.chakchak.domain.place.dto.PlaceSearchResponse;
import world.chakchak.domain.place.dto.PlaceSearchResponse.PlaceSearchItem;
import world.chakchak.domain.place.dto.PlaceSearchResult;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.place.repository.PlaceCandidateRepository;
import world.chakchak.domain.place.service.PlaceSearchClient;
import world.chakchak.domain.place.service.PlaceSearchService;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;
import world.chakchak.global.error.BusinessException;
import world.chakchak.global.error.CommonErrorCode;

/** P-01 장소 검색. Google만 가짜로 바꾸고 멤버·상태 확인과 이미 올라온 장소 표시는 실제 MySQL로 확인한다. */
@IntegrationTest
class PlaceSearchServiceTest {

    @Autowired private PlaceSearchService placeSearchService;
    @Autowired private PlaceCandidateRepository placeCandidateRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Clock clock;

    @MockitoBean private PlaceSearchClient placeSearchClient;

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("검색어 앞에 여행지를 붙이고 pageToken을 그대로 넘겨 Google을 부른다")
            void prefixesDestination() {
                RoomMember member = saveMember(saveRoom(RoomStatus.PLANNING), "민수");
                given(placeSearchClient.search("서울 시장", "token-1"))
                        .willReturn(new PlaceSearchResult(List.of(), null));

                search(member, "시장", "token-1");

                verify(placeSearchClient).search("서울 시장", "token-1");
            }

            @Test
            @DisplayName("결과의 이름, 주소, 종류와 다음 페이지 토큰을 그대로 돌려준다")
            void returnsGoogleResult() {
                RoomMember member = saveMember(saveRoom(RoomStatus.PLANNING), "민수");
                given(placeSearchClient.search(anyString(), any()))
                        .willReturn(
                                new PlaceSearchResult(
                                        List.of(googlePlace("place-1", "광장시장")), "next-token"));

                PlaceSearchResponse response = search(member, "시장", null);

                assertThat(response.places())
                        .containsExactly(
                                new PlaceSearchItem("place-1", "광장시장", "서울 종로구", "market", false));
                assertThat(response.nextPageToken()).isEqualTo("next-token");
            }

            @Test
            @DisplayName("이 방에 이미 올라온 장소만 alreadyRegistered가 true이고 다른 방의 후보는 영향이 없다")
            void marksAlreadyRegistered() {
                Room room = saveRoom(RoomStatus.PLANNING);
                RoomMember member = saveMember(room, "민수");
                Room otherRoom = saveRoom(RoomStatus.PLANNING);
                saveCandidate(room, "place-1", member);
                saveCandidate(otherRoom, "place-2", saveMember(otherRoom, "철수"));
                given(placeSearchClient.search(anyString(), any()))
                        .willReturn(
                                new PlaceSearchResult(
                                        List.of(
                                                googlePlace("place-1", "광장시장"),
                                                googlePlace("place-2", "통인시장"),
                                                googlePlace("place-3", "망원시장")),
                                        null));

                PlaceSearchResponse response = search(member, "시장", null);

                assertThat(response.places())
                        .extracting(
                                PlaceSearchItem::googlePlaceId, PlaceSearchItem::alreadyRegistered)
                        .containsExactly(
                                tuple("place-1", true),
                                tuple("place-2", false),
                                tuple("place-3", false));
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("다른 방의 멤버면 FORBIDDEN으로 막고 Google을 부르지 않는다")
            void rejectsNonMember() {
                Room room = saveRoom(RoomStatus.PLANNING);
                RoomMember outsider = saveMember(saveRoom(RoomStatus.PLANNING), "민수");

                assertThatThrownBy(() ->
                                placeSearchService.search(
                                        room.getId(), outsider.getUser().getId(), "시장", null))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(CommonErrorCode.FORBIDDEN));
                verify(placeSearchClient, never()).search(anyString(), any());
            }

            @Test
            @DisplayName("방을 나간 멤버면 FORBIDDEN으로 막고 Google을 부르지 않는다")
            void rejectsLeftMember() {
                RoomMember member = saveMember(saveRoom(RoomStatus.PLANNING), "민수");
                jdbcTemplate.update(
                        "UPDATE plan_room_member SET left_at = NOW(6) WHERE id = ?",
                        member.getId());

                assertThatThrownBy(() -> search(member, "시장", null))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(CommonErrorCode.FORBIDDEN));
                verify(placeSearchClient, never()).search(anyString(), any());
            }

            @Test
            @DisplayName("일정 조율(PLANNING) 중이 아닌 방이면 INVALID_STATUS로 막고 Google을 부르지 않는다")
            void rejectsRoomNotPlanning() {
                RoomMember member = saveMember(saveRoom(RoomStatus.SURVEYING), "민수");

                assertThatThrownBy(() -> search(member, "시장", null))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e ->
                                        assertThat(e.getErrorCode())
                                                .isEqualTo(CommonErrorCode.INVALID_STATUS));
                verify(placeSearchClient, never()).search(anyString(), any());
            }
        }
    }

    private PlaceSearchResponse search(RoomMember member, String query, String pageToken) {
        return placeSearchService.search(
                member.getRoom().getId(), member.getUser().getId(), query, pageToken);
    }

    private GooglePlace googlePlace(String googlePlaceId, String name) {
        return new GooglePlace(googlePlaceId, name, "서울 종로구", "market", null, null);
    }

    private void saveCandidate(Room room, String googlePlaceId, RoomMember member) {
        placeCandidateRepository.saveAndFlush(
                PlaceCandidate.create(room, googlePlaceId, "광장시장", null, null, null, null, member));
    }

    private Room saveRoom(RoomStatus status) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return roomRepository.saveAndFlush(
                Room.create(
                        "여행 " + suffix,
                        "서울",
                        LocalDate.of(2027, 7, 1),
                        LocalDate.of(2027, 7, 3),
                        suffix,
                        status,
                        clock));
    }

    private RoomMember saveMember(Room room, String name) {
        User user =
                userRepository.saveAndFlush(
                        User.create(UUID.randomUUID() + "@example.com", name, "hashed-password"));
        return roomMemberRepository.saveAndFlush(RoomMember.create(room, user, clock));
    }
}
