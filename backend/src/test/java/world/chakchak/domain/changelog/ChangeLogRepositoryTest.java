package world.chakchak.domain.changelog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.changelog.entity.ActionStatus;
import world.chakchak.domain.changelog.entity.ChangeLog;
import world.chakchak.domain.changelog.entity.EntityType;
import world.chakchak.domain.changelog.entity.EventKind;
import world.chakchak.domain.changelog.repository.ChangeLogRepository;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;

@IntegrationTest
class ChangeLogRepositoryTest {

    @Autowired private ChangeLogRepository changeLogRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;

    @Nested
    class Create {

        @Test
        @DisplayName("방과 행위자 및 변경 정보를 활동 기록으로 저장한다")
        void savesRoomActivity() {
            Room room = saveRoom();
            RoomMember actor = saveMember(room);

            ChangeLog saved =
                    save(
                            room,
                            actor,
                            EntityType.PLACE_CANDIDATE,
                            987_654L,
                            EventKind.PLACE_REGISTERED,
                            ActionStatus.CREATE,
                            "민수님이 해운대 시장을 올렸어요");
            ChangeLog found = changeLogRepository.findById(saved.getId()).orElseThrow();

            assertThat(found.getRoom().getId()).isEqualTo(room.getId());
            assertThat(found.getActor().getId()).isEqualTo(actor.getId());
            assertThat(found.getEntityType()).isEqualTo(EntityType.PLACE_CANDIDATE);
            assertThat(found.getEntityId()).isEqualTo(987_654L);
            assertThat(found.getEventKind()).isEqualTo(EventKind.PLACE_REGISTERED);
            assertThat(found.getActionStatus()).isEqualTo(ActionStatus.CREATE);
            assertThat(found.getSummary()).isEqualTo("민수님이 해운대 시장을 올렸어요");
            assertThat(found.getCreatedAt()).isNotNull();
            assertThat(found.getModifiedAt()).isNotNull();
        }

        @Test
        @DisplayName("시스템 활동을 행위자 없이 저장한다")
        void savesSystemActivityWithoutActor() {
            Room room = saveRoom();

            ChangeLog saved =
                    save(
                            room,
                            null,
                            EntityType.PLAN_ROOM,
                            room.getId(),
                            EventKind.SURVEY_CLOSED,
                            ActionStatus.UPDATE,
                            "설문이 마감됐어요");

            assertThat(changeLogRepository.findById(saved.getId()).orElseThrow().getActor())
                    .isNull();
        }
    }

    @Nested
    class Read {

        @Test
        @DisplayName("방 ID와 커서로 타임라인 및 재연결 기록을 조회한다")
        void readsTimelineAndReplayByRoomCursor() {
            Room room = saveRoom();
            ChangeLog first = recordActivity(room, EventKind.MEMBER_JOINED);
            ChangeLog second = recordActivity(room, EventKind.PLACE_REGISTERED);
            ChangeLog third = recordActivity(room, EventKind.PLAN_FINALIZED);

            assertThat(
                            changeLogRepository
                                    .findAllByRoom_IdOrderByIdDesc(
                                            room.getId(), PageRequest.of(0, 2))
                                    .getContent())
                    .extracting(ChangeLog::getId)
                    .containsExactly(third.getId(), second.getId());
            assertThat(
                            changeLogRepository.findAllByRoom_IdAndIdLessThanOrderByIdDesc(
                                    room.getId(), second.getId(), PageRequest.of(0, 2)))
                    .extracting(ChangeLog::getId)
                    .containsExactly(first.getId());
            assertThat(
                            changeLogRepository.findTop201ByRoom_IdAndIdGreaterThanOrderByIdAsc(
                                    room.getId(), first.getId()))
                    .extracting(ChangeLog::getId)
                    .containsExactly(second.getId(), third.getId());
        }

        @Test
        @DisplayName("비접속 요약 조회에서 본인 변경은 제외하고 다른 멤버와 시스템 변경을 포함한다")
        void findsOtherMemberAndSystemChangesSinceSeenTime() {
            Room room = saveRoom();
            RoomMember currentMember = saveMember(room);
            RoomMember otherMember = saveMember(room);
            save(
                    room,
                    currentMember,
                    EntityType.PLACE_CANDIDATE,
                    1L,
                    EventKind.PLACE_REGISTERED,
                    ActionStatus.CREATE,
                    "내 장소");
            ChangeLog otherMemberLog =
                    save(
                            room,
                            otherMember,
                            EntityType.PLACE_CANDIDATE,
                            2L,
                            EventKind.PLACE_REGISTERED,
                            ActionStatus.CREATE,
                            "다른 멤버의 장소");
            ChangeLog systemLog =
                    save(
                            room,
                            null,
                            EntityType.PLAN_ROOM,
                            room.getId(),
                            EventKind.SURVEY_CLOSED,
                            ActionStatus.UPDATE,
                            "설문 마감");

            assertThat(
                            changeLogRepository.findAfterSeenExcludingActor(
                                    room.getId(),
                                    currentMember.getId(),
                                    LocalDateTime.of(2000, 1, 1, 0, 0)))
                    .extracting(ChangeLog::getId)
                    .containsExactly(otherMemberLog.getId(), systemLog.getId());
        }
    }

    private ChangeLog recordActivity(Room room, EventKind eventKind) {
        return save(
                room,
                null,
                EntityType.PLAN_ROOM,
                room.getId(),
                eventKind,
                ActionStatus.UPDATE,
                eventKind.name());
    }

    private ChangeLog save(
            Room room,
            RoomMember actor,
            EntityType entityType,
            Long entityId,
            EventKind eventKind,
            ActionStatus actionStatus,
            String summary) {
        return new TransactionTemplate(transactionManager)
                .execute(status ->
                        changeLogRepository.save(
                                ChangeLog.create(
                                        room,
                                        actor,
                                        entityType,
                                        entityId,
                                        eventKind,
                                        actionStatus,
                                        summary)));
    }

    private RoomMember saveMember(Room room) {
        User user =
                userRepository.saveAndFlush(
                        User.create(UUID.randomUUID() + "@example.com", "테스터", "hashed-password"));
        return roomMemberRepository.saveAndFlush(RoomMember.create(room, user, clock));
    }

    private Room saveRoom() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return roomRepository.saveAndFlush(
                Room.create(
                        "여행 " + suffix,
                        "부산",
                        LocalDate.of(2027, 7, 1),
                        LocalDate.of(2027, 7, 3),
                        suffix,
                        RoomStatus.SURVEYING,
                        clock));
    }
}
