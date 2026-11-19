package world.chakchak.domain.room.service;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import world.chakchak.global.error.BusinessException;
import world.chakchak.global.error.CommonErrorCode;

/** 프로세스 안에서 IP별 초대 미리보기 요청을 고정 1분 창으로 제한한다. */
@Component
@RequiredArgsConstructor
public class InvitePreviewRateLimiter {

    private static final int MAX_REQUESTS_PER_WINDOW = 20;
    private static final long WINDOW_SECONDS = 60;

    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong cleanedWindow = new AtomicLong(Long.MIN_VALUE);

    public void check(String clientIp) {
        long currentWindow = Math.floorDiv(clock.instant().getEpochSecond(), WINDOW_SECONDS);
        AtomicBoolean overLimit = new AtomicBoolean();
        String key = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;

        if (cleanedWindow.getAndSet(currentWindow) != currentWindow) {
            windows.entrySet().removeIf(entry -> entry.getValue().window() < currentWindow);
        }

        windows.compute(
                key,
                (ignored, previous) -> {
                    Window active =
                            previous == null || previous.window() != currentWindow
                                    ? new Window(currentWindow, 0)
                                    : previous;
                    if (active.requests() >= MAX_REQUESTS_PER_WINDOW) {
                        overLimit.set(true);
                        return active;
                    }
                    return new Window(currentWindow, active.requests() + 1);
                });

        if (overLimit.get()) {
            throw new BusinessException(CommonErrorCode.TOO_MANY_REQUESTS);
        }
    }

    private record Window(long window, int requests) {}
}
