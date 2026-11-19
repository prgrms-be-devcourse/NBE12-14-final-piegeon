package world.chakchak.domain.place;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import world.chakchak.domain.place.dto.PlaceStage;

/** 칸반 칸 계산 규칙(명세 8-1) 단위 테스트. */
class PlaceStageTest {

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("확정된 후보는 반응 수와 상관없이 CONFIRMED다")
            void confirmedWins() {
                assertThat(PlaceStage.of(true, 1, 4)).isEqualTo(PlaceStage.CONFIRMED);
            }

            @Test
            @DisplayName("활동 멤버 전원이 가고 싶어요면 FULL이다")
            void allMembersWantIsFull() {
                assertThat(PlaceStage.of(false, 4, 4)).isEqualTo(PlaceStage.FULL);
            }

            @Test
            @DisplayName("전원은 아니지만 2명 이상이 가고 싶어요면 PARTIAL이다")
            void twoOrMoreIsPartial() {
                assertThat(PlaceStage.of(false, 2, 4)).isEqualTo(PlaceStage.PARTIAL);
                assertThat(PlaceStage.of(false, 3, 4)).isEqualTo(PlaceStage.PARTIAL);
            }

            @Test
            @DisplayName("올린 사람 1명만 가고 싶어요면 COMPLEMENT다")
            void onlySuggesterIsComplement() {
                assertThat(PlaceStage.of(false, 1, 4)).isEqualTo(PlaceStage.COMPLEMENT);
            }

            @Test
            @DisplayName("혼자 있는 방에서는 올린 사람 1명의 가고 싶어요로 바로 FULL이다")
            void soloRoomIsFull() {
                assertThat(PlaceStage.of(false, 1, 1)).isEqualTo(PlaceStage.FULL);
            }
        }
    }
}
