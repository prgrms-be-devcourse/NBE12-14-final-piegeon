package world.chakchak.domain.changelog.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import world.chakchak.domain.changelog.event.ChangeLogRecordedEvent;

@Component
@RequiredArgsConstructor
public class ChangeLogStreamListener {

    private final ChangeLogStreamService changeLogStreamService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAfterCommit(ChangeLogRecordedEvent event) {
        changeLogStreamService.publish(event.activity());
    }
}
