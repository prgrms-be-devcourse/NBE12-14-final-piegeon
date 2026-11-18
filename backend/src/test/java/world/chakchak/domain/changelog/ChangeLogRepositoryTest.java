package world.chakchak.domain.changelog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.changelog.entity.ActionStatus;
import world.chakchak.domain.changelog.entity.ChangeLog;
import world.chakchak.domain.changelog.entity.EntityType;
import world.chakchak.domain.changelog.entity.EventKind;
import world.chakchak.domain.changelog.errorcode.ChangeLogErrorCode;
import world.chakchak.domain.changelog.repository.ChangeLogRepository;
import world.chakchak.domain.changelog.service.ChangeLogService;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.user.entity.User;
import world.chakchak.domain.user.repository.UserRepository;
import world.chakchak.global.error.BusinessException;

@IntegrationTest
class ChangeLogRepositoryTest {

    @Autowired private ChangeLogRepository changeLogRepository;
    @Autowired private ChangeLogService changeLogService;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomMemberRepository roomMemberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;

    @Nested
    class Create {

        @Test
        @DisplayName("방과 행위자 및 변경 정보를 같은 방 활동 기록으로 저장한다")
        void savesRoomActivity() {
            Room room = saveRoom();
            RoomMember actor = saveMember(room);

            ChangeLog saved =
                    record(
                            room.getId(),
                            actor.getId(),
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
        @DisplayName("시스템 활동은 행위자 없이 기록한다")
        void savesSystemActivityWithoutActor() {
            Room room = saveRoom();

            ChangeLog saved =
                    record(
                            room.getId(),
                            null,
                            EntityType.PLAN_ROOM,
                            room.getId(),
                            EventKind.SURVEY_CLOSED,
                            ActionStatus.UPDATE,
                            "설문이 마감됐어요");

            assertThat(changeLogRepository.findById(saved.getId()).orElseThrow().getActor())
                    .isNull();
        }

        @Test
        @DisplayName("다른 방의 멤버를 행위자로 기록하지 않는다")
        void rejectsActorFromAnotherRoom() {
            Room room = saveRoom();
            Room otherRoom = saveRoom();
            RoomMember actor = saveMember(otherRoom);

            assertThatThrownBy(() ->
                            record(
                                    room.getId(),
                                    actor.getId(),
                                    EntityType.MEMBER,
                                    actor.getId(),
                                    EventKind.MEMBER_JOINED,
                                    ActionStatus.CREATE,
                                    "멤버가 합류했어요"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(error -> ((BusinessException) error).getErrorCode())
                    .isEqualTo(ChangeLogErrorCode.ACTOR_NOT_IN_ROOM);
        }

        @Test
        @DisplayName("방 활동 기록은 기존 트랜잭션 안에서만 저장한다")
        void requiresCallerTransaction() {
            Room room = saveRoom();

            assertThatThrownBy(() ->
                            changeLogService.record(
                                    room.getId(),
                                    null,
                                    EntityType.PLAN_ROOM,
                                    room.getId(),
                                    EventKind.SURVEY_CLOSED,
                                    ActionStatus.UPDATE,
                                    "설문이 마감됐어요"))
                    .isInstanceOf(IllegalTransactionStateException.class);
        }

        @Test
        @DisplayName("같은 방의 변경 트랜잭션을 직렬화해 로그 ID 순서로 커밋한다")
        void serializesLogIdsWithinRoom() throws Exception {
            Room room = saveRoom();
            CountDownLatch firstLogSaved = new CountDownLatch(1);
            CountDownLatch secondWriterStarted = new CountDownLatch(1);
            CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);

            try {
                Future<Long> firstLogId =
                        executor.submit(() ->
                                new TransactionTemplate(transactionManager)
                                        .execute(status -> {
                                            ChangeLog log =
                                                    changeLogService.record(
                                                            room.getId(),
                                                            null,
                                                            EntityType.PLAN_ROOM,
                                                            room.getId(),
                                                            EventKind.SURVEY_CLOSED,
                                                            ActionStatus.UPDATE,
                                                            "first");
                                            firstLogSaved.countDown();
                                            await(releaseFirstTransaction);
                                            return log.getId();
                                        }));

                assertThat(firstLogSaved.await(10, TimeUnit.SECONDS)).isTrue();
                Future<Long> secondLogId =
                        executor.submit(() ->
                                new TransactionTemplate(transactionManager)
                                        .execute(status -> {
                                            secondWriterStarted.countDown();
                                            return changeLogService
                                                    .record(
                                                            room.getId(),
                                                            null,
                                                            EntityType.PLAN_ROOM,
                                                            room.getId(),
                                                            EventKind.COORDINATION_COMPLETED,
                                                            ActionStatus.UPDATE,
                                                            "second")
                                                    .getId();
                                        }));

                assertThat(secondWriterStarted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> secondLogId.get(500, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);

                releaseFirstTransaction.countDown();

                assertThat(secondLogId.get(10, TimeUnit.SECONDS))
                        .isGreaterThan(firstLogId.get(10, TimeUnit.SECONDS));
            } finally {
                releaseFirstTransaction.countDown();
                executor.shutdownNow();
            }
        }
    }

    @Nested
    class Read {

        @Test
        @DisplayName("방 ID 커서는 최신순 타임라인과 오름차순 재연결 기록을 조회한다")
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
                            changeLogRepository
                                    .findAllByRoom_IdAndIdLessThanOrderByIdDesc(
                                            room.getId(), second.getId(), PageRequest.of(0, 2))
                                    .getContent())
                    .extracting(ChangeLog::getId)
                    .containsExactly(first.getId());
            assertThat(
                            changeLogRepository.findTop201ByRoom_IdAndIdGreaterThanOrderByIdAsc(
                                    room.getId(), first.getId()))
                    .extracting(ChangeLog::getId)
                    .containsExactly(second.getId(), third.getId());
        }

        @Test
        @DisplayName("비접속 요약용 조회는 본인 변경을 제외하고 타 멤버와 시스템 변경을 포함한다")
        void findsOtherMemberAndSystemChangesSinceSeenTime() {
            Room room = saveRoom();
            RoomMember currentMember = saveMember(room);
            RoomMember otherMember = saveMember(room);
            record(
                    room.getId(),
                    currentMember.getId(),
                    EntityType.PLACE_CANDIDATE,
                    1L,
                    EventKind.PLACE_REGISTERED,
                    ActionStatus.CREATE,
                    "내 장소");
            ChangeLog otherMemberLog =
                    record(
                            room.getId(),
                            otherMember.getId(),
                            EntityType.PLACE_CANDIDATE,
                            2L,
                            EventKind.PLACE_REGISTERED,
                            ActionStatus.CREATE,
                            "다른 멤버의 장소");
            ChangeLog systemLog =
                    record(
                            room.getId(),
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
        return record(
                room.getId(),
                null,
                EntityType.PLAN_ROOM,
                room.getId(),
                eventKind,
                ActionStatus.UPDATE,
                eventKind.name());
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test transaction release");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Test transaction was interrupted", exception);
        }
    }

    private ChangeLog record(
            Long roomId,
            Long actorId,
            EntityType entityType,
            Long entityId,
            EventKind eventKind,
            ActionStatus actionStatus,
            String summary) {
        return new TransactionTemplate(transactionManager)
                .execute(status ->
                        changeLogService.record(
                                roomId,
                                actorId,
                                entityType,
                                entityId,
                                eventKind,
                                actionStatus,
                                summary));
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
