package world.chakchak.domain.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.user.entity.User;

/** PlaceCandidate 엔티티 규칙 단위 테스트. Spring 없이 돈다. */
class PlaceCandidateTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2027, 1, 1, 12, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);

    private final Room room =
            Room.create(
                    "여름 여행",
                    "부산",
                    LocalDate.of(2027, 7, 1),
                    LocalDate.of(2027, 7, 3),
                    "ABCD1234",
                    RoomStatus.SURVEYING,
                    CLOCK);
    private final RoomMember member =
            RoomMember.create(
                    room, User.create("tester@example.com", "테스터", "hashed-password"), CLOCK);

    private PlaceCandidate newCandidate() {
        return PlaceCandidate.create(
                room,
                "place-1",
                "광장시장",
                "market",
                "서울특별시 종로구 창경궁로 88",
                "https://maps.google.com/?cid=1",
                "places/place-1/photos/p1",
                member);
    }

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("장소 후보는 전달받은 방과 Google 장소 정보를 보유하고 처음에는 미확정이다")
            void createsCandidate() {
                PlaceCandidate candidate = newCandidate();

                assertThat(candidate.getRoom()).isSameAs(room);
                assertThat(candidate.getGooglePlaceId()).isEqualTo("place-1");
                assertThat(candidate.getName()).isEqualTo("광장시장");
                assertThat(candidate.getPlaceType()).isEqualTo("market");
                assertThat(candidate.getAddress()).isEqualTo("서울특별시 종로구 창경궁로 88");
                assertThat(candidate.getGoogleMapsUrl())
                        .isEqualTo("https://maps.google.com/?cid=1");
                assertThat(candidate.getPhotoName()).isEqualTo("places/place-1/photos/p1");
                assertThat(candidate.getSuggestedBy()).isSameAs(member);
                assertThat(candidate.getConfirmedAt()).isNull();
                assertThat(candidate.isConfirmed()).isFalse();
            }
        }
    }

    @Nested
    class Update {

        @Nested
        class Success {

            @Test
            @DisplayName("장소 후보를 확정하면 확정 시각이 현재 시각으로 채워진다")
            void confirmsCandidate() {
                PlaceCandidate candidate = newCandidate();

                candidate.confirm(CLOCK);

                assertThat(candidate.getConfirmedAt()).isEqualTo(NOW);
                assertThat(candidate.isConfirmed()).isTrue();
            }

            @Test
            @DisplayName("이미 확정된 장소 후보를 다시 확정해도 에러 없이 끝나고 확정 시각은 처음 값 그대로다")
            void confirmIsIdempotent() {
                PlaceCandidate candidate = newCandidate();
                candidate.confirm(CLOCK);
                Clock later = Clock.offset(CLOCK, Duration.ofHours(1));

                candidate.confirm(later);

                assertThat(candidate.getConfirmedAt()).isEqualTo(NOW);
                assertThat(candidate.isConfirmed()).isTrue();
            }
        }
    }
}
