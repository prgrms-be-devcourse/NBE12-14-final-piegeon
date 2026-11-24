package world.chakchak.domain.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomRepository;

@IntegrationTest
class RoomRepositoryTest {

    @Autowired private RoomRepository roomRepository;
    @Autowired private Clock clock;

    @Test
    @DisplayName("방을 저장한 뒤 ID로 조회하면 enum 문자열과 날짜가 유지된다")
    void savesAndFindsRoom() {
        Room saved =
                roomRepository.save(
                        Room.create(
                                "여름 여행",
                                "일본",
                                LocalDate.of(2027, 7, 1),
                                LocalDate.of(2027, 7, 3),
                                "ABCD1234",
                                RoomStatus.SURVEYING,
                                clock));

        Room found = roomRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(RoomStatus.SURVEYING);
        assertThat(found.getStartDate()).isEqualTo(LocalDate.of(2027, 7, 1));
        assertThat(found.getOpinionDeadlineAt()).isNotNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getModifiedAt()).isNotNull();
    }

    @Nested
    class Read {

        private Room saveRoom(LocalDate startDate, RoomStatus status) {
            return roomRepository.saveAndFlush(
                    Room.create(
                            "스케줄러 대상",
                            "부산",
                            startDate,
                            startDate.plusDays(1),
                            UUID.randomUUID().toString().substring(0, 8),
                            status,
                            clock));
        }

        // 다른 테스트가 남긴 방이 섞일 수 있어 이 테스트에서 만든 방만 확인한다
        @Nested
        @Transactional
        class Success {

            @Test
            @DisplayName("의견 마감이 지났고 설문이 마감되지 않았으며 아직 D-1 전인 SURVEYING 방만 찾는다")
            void findsRoomsPastOpinionDeadline() {
                LocalDate farStart = LocalDate.now(clock).plusDays(30);
                Room overdue = saveRoom(farStart, RoomStatus.SURVEYING);
                Room closed = saveRoom(farStart, RoomStatus.SURVEYING);
                closed.closeSurveyIfReady(1, 1, closed.getOpinionDeadlineAt());
                roomRepository.flush();
                Room planning = saveRoom(farStart, RoomStatus.PLANNING);
                LocalDateTime now = overdue.getOpinionDeadlineAt().plusMinutes(1);
                LocalDate tomorrow = now.toLocalDate().plusDays(1);
                Room atTally = saveRoom(tomorrow, RoomStatus.SURVEYING);

                List<Long> roomIds =
                        roomRepository.findRoomIdsPastOpinionDeadline(
                                RoomStatus.SURVEYING, now, tomorrow);

                assertThat(roomIds)
                        .contains(overdue.getId())
                        .doesNotContain(closed.getId(), planning.getId(), atTally.getId());
            }

            @Test
            @DisplayName("의견 마감 전인 방은 찾지 않는다")
            void excludesRoomsBeforeOpinionDeadline() {
                Room room = saveRoom(LocalDate.now(clock).plusDays(30), RoomStatus.SURVEYING);
                LocalDateTime now = room.getOpinionDeadlineAt().minusSeconds(1);

                List<Long> roomIds =
                        roomRepository.findRoomIdsPastOpinionDeadline(
                                RoomStatus.SURVEYING, now, now.toLocalDate().plusDays(1));

                assertThat(roomIds).doesNotContain(room.getId());
            }

            @Test
            @DisplayName("D-1이 된 방 중 조율 중이거나 최종 투표 중인 방만 찾는다")
            void findsRoomsAtFinalTally() {
                LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
                Room surveying = saveRoom(tomorrow, RoomStatus.SURVEYING);
                Room reviewing = saveRoom(tomorrow, RoomStatus.REVIEWING);
                Room planning = saveRoom(tomorrow, RoomStatus.PLANNING);
                Room finalized = saveRoom(tomorrow, RoomStatus.FINALIZED);
                Room notYet = saveRoom(tomorrow.plusDays(1), RoomStatus.SURVEYING);

                List<Long> roomIds =
                        roomRepository.findRoomIdsAtFinalTally(
                                List.of(RoomStatus.SURVEYING, RoomStatus.REVIEWING), tomorrow);

                assertThat(roomIds)
                        .contains(surveying.getId(), reviewing.getId())
                        .doesNotContain(planning.getId(), finalized.getId(), notYet.getId());
            }
        }
    }
}
