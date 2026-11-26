package world.chakchak.domain.changelog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;
import world.chakchak.domain.room.entity.RoomMember;
import world.chakchak.domain.room.service.RoomMemberService;

@ExtendWith(MockitoExtension.class)
class ChangeLogStreamServiceTest {

    @Mock private ChangeLogReplayService changeLogReplayService;

    @Mock private RoomMemberService roomMemberService;

    @Nested
    class Connect {

        @Nested
        class Success {

            @Test
            @DisplayName("멤버 연결이 3개를 초과하면 가장 오래된 연결을 닫고 새 연결을 수락한다")
            void evictsOldestConnectionWhenLimitIsExceeded() throws IOException {
                Long roomId = 1L;
                Long userId = 2L;
                Long memberId = 3L;
                RoomMember member = mock(RoomMember.class);
                given(member.getId()).willReturn(memberId);
                given(roomMemberService.getActiveMember(roomId, userId)).willReturn(member);
                given(roomMemberService.isActiveMember(roomId, memberId)).willReturn(true);
                given(changeLogReplayService.getSnapshot(roomId, null))
                        .willReturn(new ChangeLogReplayService.Snapshot(null, List.of(), false));

                List<SseEmitter> emitters =
                        List.of(
                                mock(SseEmitter.class), mock(SseEmitter.class),
                                mock(SseEmitter.class), mock(SseEmitter.class));
                int[] nextEmitter = {0};
                ChangeLogStreamService service =
                        new ChangeLogStreamService(changeLogReplayService, roomMemberService) {
                            @Override
                            SseEmitter createEmitter() {
                                return emitters.get(nextEmitter[0]++);
                            }
                        };

                for (int i = 0; i < 3; i++) {
                    service.connect(roomId, userId, null);
                }
                SseEmitter accepted = service.connect(roomId, userId, null);

                assertThat(accepted).isSameAs(emitters.get(3));

                ArgumentCaptor<SseEventBuilder> eventCaptor =
                        ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);

                InOrder replacementOrder = org.mockito.Mockito.inOrder(emitters.get(0));
                replacementOrder.verify(emitters.get(0), times(2)).send(eventCaptor.capture());
                replacementOrder.verify(emitters.get(0)).complete();

                String replacedPayload =
                        eventCaptor.getAllValues().get(1).build().stream()
                                .map(ResponseBodyEmitter.DataWithMediaType::getData)
                                .map(Object::toString)
                                .collect(Collectors.joining());
                assertThat(replacedPayload).contains("event:stream.replaced").contains("data:");

                verify(emitters.get(1), never()).complete();
                verify(emitters.get(2), never()).complete();
                verify(emitters.get(3), never()).complete();
            }
        }
    }
}
