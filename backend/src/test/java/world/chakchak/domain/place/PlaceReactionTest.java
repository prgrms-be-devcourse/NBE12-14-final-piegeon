package world.chakchak.domain.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import world.chakchak.domain.place.entity.PlaceCandidate;
import world.chakchak.domain.place.entity.PlaceReaction;
import world.chakchak.domain.place.entity.PlaceReactionType;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.user.entity.User;

/** PlaceReaction 엔티티 규칙 단위 테스트. Spring 없이 돈다. */
class PlaceReactionTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK =
            Clock.fixed(LocalDateTime.of(2027, 1, 1, 12, 0).atZone(SEOUL).toInstant(), SEOUL);

    private final Room room =
            Room.create(
                    "여름 여행",
                    "부산",
                    LocalDate.of(2027, 7, 1),
                    LocalDate.of(2027, 7, 3),
                    "ABCD1234",
                    RoomStatus.PLANNING,
                    CLOCK);
    private final RoomMember member =
            RoomMember.create(
                    room, User.create("tester@example.com", "테스터", "hashed-password"), CLOCK);
    private final PlaceCandidate candidate =
            PlaceCandidate.create(room, "place-1", "광장시장", "market", null, null, null, member);

    @Nested
    class Create {

        @Nested
        class Success {

            @Test
            @DisplayName("반응은 장소 후보와 멤버, 고른 반응을 보유한다")
            void createsReaction() {
                PlaceReaction reaction =
                        PlaceReaction.create(candidate, member, PlaceReactionType.WANT);

                assertThat(reaction.getPlaceCandidate()).isSameAs(candidate);
                assertThat(reaction.getMember()).isSameAs(member);
                assertThat(reaction.getReaction()).isEqualTo(PlaceReactionType.WANT);
                assertThat(reaction.isWant()).isTrue();
            }

            @Test
            @DisplayName("별로예요 반응은 칸반 단계 계산에 쓰는 WANT가 아니다")
            void nopeIsNotWant() {
                PlaceReaction reaction =
                        PlaceReaction.create(candidate, member, PlaceReactionType.NOPE);

                assertThat(reaction.isWant()).isFalse();
            }
        }
    }

    @Nested
    class Update {

        @Nested
        class Success {

            @Test
            @DisplayName("반응을 바꾸면 새 반응으로 바뀐다")
            void changesReaction() {
                PlaceReaction reaction =
                        PlaceReaction.create(candidate, member, PlaceReactionType.WANT);

                reaction.change(PlaceReactionType.NOPE);

                assertThat(reaction.getReaction()).isEqualTo(PlaceReactionType.NOPE);
            }

            @Test
            @DisplayName("같은 반응으로 바꾸면 그대로 유지된다")
            void keepsSameReaction() {
                PlaceReaction reaction =
                        PlaceReaction.create(candidate, member, PlaceReactionType.WANT);

                reaction.change(PlaceReactionType.WANT);

                assertThat(reaction.getReaction()).isEqualTo(PlaceReactionType.WANT);
            }
        }
    }
}
