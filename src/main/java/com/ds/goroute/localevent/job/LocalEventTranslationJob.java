package com.ds.goroute.localevent.job;

import com.ds.goroute.localevent.service.LocalEventTranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Translates the free text of published events into the app languages, a few (event, language)
 * pairs per run, every two minutes by default ({@code goroute.jobs.local-event-translations.delay},
 * milliseconds). Switched by {@code EVENT/AUTO_TRANSLATE_ENABLED}. Single instance only, like
 * every job in this application.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalEventTranslationJob {

    private final LocalEventTranslationService translationService;

    @Scheduled(fixedDelayString = "${goroute.jobs.local-event-translations.delay:120000}")
    public void translatePendingEvents() {
        try {
            int written = translationService.translatePending();
            if (written > 0) {
                log.info("Translated {} local event texts", written);
            }
        } catch (RuntimeException exception) {
            log.warn("Local event translation run failed: {}", exception.getMessage());
        }
    }
}
