package com.ds.goroute.job;

import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.assistant.AssistantTurnService;
import com.ds.goroute.type.BusinessConfigKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Ends Pengoo turns the worker never finished (crashed, restarted, lost the callback), so the
 * thread stops showing "thinking", the asker gets an apology, the question is not counted and
 * the next queued question runs. Single-instance like every job here (no Redis lock).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssistantTurnTimeoutJob {

    private final AssistantTurnService turnService;
    private final BusinessConfigService businessConfig;

    @Scheduled(fixedDelay = 30_000, initialDelay = 60_000)
    public void expireStaleTurns() {
        Duration timeout = Duration.ofSeconds(businessConfig.getInt(BusinessConfigKey.ASSISTANT_TURN_TIMEOUT_SECONDS));
        for (UUID turnId : turnService.staleTurnIds(timeout)) {
            try {
                turnService.fail(turnId, "timeout");
            } catch (RuntimeException e) {
                log.warn("Assistant: could not expire turn {}: {}", turnId, e.getMessage());
            }
        }
    }
}
