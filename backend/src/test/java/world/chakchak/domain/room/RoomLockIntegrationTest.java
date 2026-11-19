package world.chakchak.domain.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import world.chakchak.IntegrationTest;
import world.chakchak.domain.room.entity.Room;
import world.chakchak.domain.room.entity.RoomStatus;
import world.chakchak.domain.room.errorcode.RoomErrorCode;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.room.service.RoomService;
import world.chakchak.global.error.BusinessException;

/** 방 행 잠금(SELECT ... FOR UPDATE) 동작 테스트. 잠금은 커밋돼야 풀리므로 테스트 전체를 트랜잭션으로 묶지 않고, 스레드마다 별도 트랜잭션을 연다. */
@IntegrationTest
class RoomLockIntegrationTest {

    // 첫 트랜잭션이 잠금을 쥐고 있는 시간
    private static final long HOLD_MILLIS = 500;
    private static final long WAIT_SECONDS = 10;

    @Autowired private RoomService roomService;
    @Autowired private RoomRepository roomRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private Clock clock;

    private final List<Long> roomIds = new ArrayList<>();
    private TransactionTemplate transaction;
    private ExecutorService pool;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        pool = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
        roomRepository.deleteAllById(roomIds);
    }

    private Room saveRoom() {
        Room room =
                roomRepository.save(
                        Room.create(
                                "여름 여행",
                                "부산",
                                LocalDate.of(2027, 7, 1),
                                LocalDate.of(2027, 7, 3),
                                UUID.randomUUID().toString().substring(0, 8),
                                RoomStatus.SURVEYING,
                                clock));
        roomIds.add(room.getId());
        return room;
    }

    /** 다른 스레드에서 방을 잠그고 HOLD_MILLIS 동안 쥐고 있다가 커밋한다. releasedAt에는 커밋 직전 시각을 남긴다. */
    private Future<?> holdLock(Long roomId, CountDownLatch locked, AtomicLong releasedAt) {
        return pool.submit(() ->
                transaction.executeWithoutResult(status -> {
                    roomService.getForUpdate(roomId);
                    locked.countDown();
                    sleep(HOLD_MILLIS);
                    releasedAt.set(System.nanoTime());
                }));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Nested
    class Read {

        @Nested
        class Success {

            @Test
            @DisplayName("같은 방을 잠그려는 두 번째 트랜잭션은 첫 트랜잭션이 커밋한 뒤에야 잠금을 얻는다")
            void serializesSameRoom() throws java.lang.Exception {
                Long roomId = saveRoom().getId();
                CountDownLatch locked = new CountDownLatch(1);
                AtomicLong releasedAt = new AtomicLong();
                Future<?> first = holdLock(roomId, locked, releasedAt);
                assertThat(locked.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

                Future<Long> second =
                        pool.submit(() ->
                                transaction.execute(status -> {
                                    roomService.getForUpdate(roomId);
                                    return System.nanoTime();
                                }));

                long acquiredAt = second.get(WAIT_SECONDS, TimeUnit.SECONDS);
                first.get(WAIT_SECONDS, TimeUnit.SECONDS);
                assertThat(acquiredAt).isGreaterThan(releasedAt.get());
            }

            @Test
            @DisplayName("다른 방은 잠긴 방을 기다리지 않고 바로 잠금을 얻는다")
            void doesNotBlockOtherRoom() throws java.lang.Exception {
                Long lockedRoomId = saveRoom().getId();
                Long otherRoomId = saveRoom().getId();
                CountDownLatch locked = new CountDownLatch(1);
                AtomicLong releasedAt = new AtomicLong();
                Future<?> first = holdLock(lockedRoomId, locked, releasedAt);
                assertThat(locked.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

                long acquiredAt =
                        transaction.execute(status -> {
                            roomService.getForUpdate(otherRoomId);
                            return System.nanoTime();
                        });

                first.get(WAIT_SECONDS, TimeUnit.SECONDS);
                assertThat(acquiredAt).isLessThan(releasedAt.get());
            }

            @Test
            @DisplayName("잠금 없는 조회는 잠긴 방도 기다리지 않고 읽는다")
            void doesNotBlockPlainRead() throws java.lang.Exception {
                Long roomId = saveRoom().getId();
                CountDownLatch locked = new CountDownLatch(1);
                AtomicLong releasedAt = new AtomicLong();
                Future<?> first = holdLock(roomId, locked, releasedAt);
                assertThat(locked.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

                long readAt =
                        transaction.execute(status -> {
                            roomRepository.findById(roomId).orElseThrow();
                            return System.nanoTime();
                        });

                first.get(WAIT_SECONDS, TimeUnit.SECONDS);
                assertThat(readAt).isLessThan(releasedAt.get());
            }
        }

        @Nested
        class Exception {

            @Test
            @DisplayName("없는 방을 잠그려 하면 ROOM_NOT_FOUND를 던진다")
            void rejectsMissingRoom() {
                assertThatThrownBy(() ->
                                transaction.executeWithoutResult(
                                        status -> roomService.getForUpdate(Long.MAX_VALUE)))
                        .isInstanceOf(BusinessException.class)
                        .extracting("errorCode")
                        .isEqualTo(RoomErrorCode.ROOM_NOT_FOUND);
            }

            @Test
            @DisplayName("트랜잭션 밖에서 방을 잠그려 하면 잠금이 바로 풀리므로 예외를 던진다")
            void rejectsCallOutsideTransaction() {
                Long roomId = saveRoom().getId();

                assertThatThrownBy(() -> roomService.getForUpdate(roomId))
                        .isInstanceOf(IllegalTransactionStateException.class);
            }
        }
    }
}
