package world.chakchak.domain.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import world.chakchak.domain.room.dto.CreateRoomRequest;
import world.chakchak.domain.room.errorcode.RoomErrorCode;
import world.chakchak.domain.room.repository.RoomMemberRepository;
import world.chakchak.domain.room.repository.RoomRepository;
import world.chakchak.domain.room.service.InvitePreviewRateLimiter;
import world.chakchak.domain.room.service.RoomMemberService;
import world.chakchak.domain.room.service.RoomService;
import world.chakchak.domain.schedule.service.ScheduleDayService;
import world.chakchak.global.error.BusinessException;

class RoomServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 0, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);

    @Nested
    class Create {

        @Nested
        class Exception {

            @Test
            @DisplayName("출발일 자정이 현재 시각부터 24시간 이내이면 방을 만들지 않는다")
            void rejectsDepartureWithin24Hours() {
                LocalDate startDate = NOW.toLocalDate().plusDays(1);

                assertThatThrownBy(() -> create(startDate, startDate))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                exception ->
                                        assertThat(exception.getErrorCode())
                                                .isEqualTo(RoomErrorCode.DEPARTURE_TOO_SOON));
            }

            @Test
            @DisplayName("출발일이 오늘부터 1년 이상 뒤이면 방을 만들지 않는다")
            void rejectsDepartureAtOneYearBoundary() {
                LocalDate startDate = NOW.toLocalDate().plusYears(1);

                assertThatThrownBy(() -> create(startDate, startDate))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                exception ->
                                        assertThat(exception.getErrorCode())
                                                .isEqualTo(RoomErrorCode.DEPARTURE_TOO_FAR));
            }

            @Test
            @DisplayName("종료일이 시작일보다 빠르면 방을 만들지 않는다")
            void rejectsEndDateBeforeStartDate() {
                LocalDate startDate = NOW.toLocalDate().plusDays(5);

                assertThatThrownBy(() -> create(startDate, startDate.minusDays(1)))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                exception ->
                                        assertThat(exception.getErrorCode())
                                                .isEqualTo(RoomErrorCode.INVALID_DATE_RANGE));
            }

            @Test
            @DisplayName("여행 기간이 31일을 초과하면 방을 만들지 않는다")
            void rejectsTripLongerThan31Days() {
                LocalDate startDate = NOW.toLocalDate().plusDays(5);

                assertThatThrownBy(() -> create(startDate, startDate.plusDays(31)))
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                exception ->
                                        assertThat(exception.getErrorCode())
                                                .isEqualTo(RoomErrorCode.INVALID_DATE_RANGE));
            }
        }
    }

    private void create(LocalDate startDate, LocalDate endDate) {
        RoomService service =
                new RoomService(
                        mock(RoomRepository.class),
                        mock(RoomMemberRepository.class),
                        mock(InvitePreviewRateLimiter.class),
                        mock(RoomMemberService.class),
                        mock(ScheduleDayService.class),
                        CLOCK,
                        mock(PlatformTransactionManager.class));
        service.create(1L, new CreateRoomRequest("여행", "서울", startDate, endDate));
    }
}
