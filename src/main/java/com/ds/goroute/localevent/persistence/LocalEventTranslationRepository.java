package com.ds.goroute.localevent.persistence;

import com.ds.goroute.localevent.domain.LocalEventTranslation;
import com.ds.goroute.localevent.domain.LocalEventTranslationTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence facade over {@link LocalEventTranslationMapper}. */
@Repository
@RequiredArgsConstructor
public class LocalEventTranslationRepository {

    private final LocalEventTranslationMapper mapper;

    public Optional<LocalEventTranslation> find(UUID eventId, String locale) {
        return Optional.ofNullable(mapper.find(eventId, locale));
    }

    public List<LocalEventTranslation> findByEvent(UUID eventId) {
        return mapper.findByEvent(eventId);
    }

    public List<LocalEventTranslationTask> findPending(List<String> locales, LocalDateTime now,
                                                       int maxAttempts, int limit) {
        return locales.isEmpty() ? List.of() : mapper.findPending(locales, now, maxAttempts, limit);
    }

    public void saveAi(LocalEventTranslation translation) {
        mapper.upsertAi(translation);
    }

    public void saveFailed(UUID eventId, String locale, String sourceHash, LocalDateTime now) {
        mapper.upsertFailed(eventId, locale, sourceHash, now);
    }

    public void saveManual(LocalEventTranslation translation) {
        mapper.upsertManual(translation);
    }

    public boolean delete(UUID eventId, String locale) {
        return mapper.delete(eventId, locale) == 1;
    }
}
