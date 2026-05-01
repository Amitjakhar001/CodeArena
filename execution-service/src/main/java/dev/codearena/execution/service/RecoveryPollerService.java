package dev.codearena.execution.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RecoveryPollerService {

    private final ExecutionService executions;

    public RecoveryPollerService(ExecutionService executions) {
        this.executions = executions;
    }

    @Scheduled(fixedDelayString = "${judge0.recovery-poll-interval-ms:30000}",
               initialDelayString = "${judge0.recovery-poll-initial-delay-ms:30000}")
    public void poll() {
        executions.recoverStuckExecutions();
    }
}
