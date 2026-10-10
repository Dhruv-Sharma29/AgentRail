package com.agentflow.execution;

import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "agentflow.execution.worker-enabled", havingValue = "true", matchIfMissing = true)
class ExecutionWorker {
    private static final Logger log = LoggerFactory.getLogger(ExecutionWorker.class);
    private final ExecutionService service;
    ExecutionWorker(ExecutionService service) { this.service = service; }
    @Scheduled(fixedDelayString = "${agentflow.execution.poll-delay-ms:1000}")
    void tick() {
        for (var id : service.runnable()) {
            try { service.advance(id); }
            catch (RuntimeException failure) {
                log.warn("Could not advance run {} ({}); checkpoint retained for next poll", id, failure.getClass().getSimpleName());
            }
        }
    }
}
