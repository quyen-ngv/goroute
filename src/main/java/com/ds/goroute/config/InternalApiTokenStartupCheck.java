package com.ds.goroute.config;

import com.ds.goroute.config.filter.InternalApiAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reports at startup which internal-callback tokens are missing.
 *
 * <p>Without it a missing token is invisible until a worker calls back and is rejected
 * with 503, at which point the symptom operators actually see is a nationwide or
 * place-detail-refresh job that never leaves PROCESSING -- the callback that would have
 * moved it to COMPLETED/FAILED is the very thing being rejected.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InternalApiTokenStartupCheck {

    private final InternalApiProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void reportMissingTokens() {
        if (isBlank(properties.scrapeCallbackToken())) {
            log.error("{} is not set: scrape worker callbacks on /v1/api/internal/place-import-jobs/** "
                            + "will be rejected with 503 and nationwide / place-detail-refresh jobs will "
                            + "stay PROCESSING until the watchdog abandons them",
                    InternalApiAuthenticationFilter.SCRAPE_CALLBACK_SETTING);
        }
        if (isBlank(properties.aiTripToken())) {
            log.error("{} is not set: AI trip worker callbacks on /v1/api/internal/ai-trip-generations/** "
                            + "will be rejected with 503",
                    InternalApiAuthenticationFilter.AI_TRIP_SETTING);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
