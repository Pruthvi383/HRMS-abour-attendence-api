package com.example.hrms.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class OvertimeSettledEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSettled(OvertimeSettledEvent event) {
        try {
            log.info("SMS NOTIFICATION: Sending to {} ({}) - Overtime for {} settled: Rs. {}",
                event.getWorkerName(),
                event.getPhone(),
                event.getMonth(),
                event.getTotalAmount());
        } catch (RuntimeException e) {
            log.error("SMS notification failed for worker {} month {}. Settlement data is intact. Error: {}",
                event.getWorkerId(), event.getMonth(), e.getMessage());
        }
    }
}
