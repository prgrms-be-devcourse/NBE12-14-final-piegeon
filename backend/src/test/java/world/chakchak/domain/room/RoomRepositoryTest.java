package world.chakchak.domain.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomRepository;

@IntegrationTest
class RoomRepositoryTest {

    @Autowired private RoomRepository roomRepository;

    @Test
    @DisplayName("방을 저장한 뒤 ID로 조회하면 enum 문자열과 날짜가 유지된다")
    void savesAndFindsRoom() {
        Room saved =
                roomRepository.save(
                        Room.create(
                                "여름 여행",
                                "부산",
                                LocalDate.of(2027, 7, 1),
                                LocalDate.of(2027, 7, 3),
                                "ABCD1234",
                                RoomStatus.SURVEYING,
                                null,
                                null,
                                null));

        Room found = roomRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(RoomStatus.SURVEYING);
        assertThat(found.getStartDate()).isEqualTo(LocalDate.of(2027, 7, 1));
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getModifiedAt()).isNotNull();
    }
}
