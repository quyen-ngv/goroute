package com.ds.goroute.localevent.persistence;

import com.ds.goroute.localevent.domain.LocalEventTranslation;
import com.ds.goroute.localevent.domain.LocalEventTranslationTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** {@code local_event_translations} (V285). */
@Mapper
public interface LocalEventTranslationMapper {

    LocalEventTranslation find(@Param("eventId") UUID eventId, @Param("locale") String locale);

    List<LocalEventTranslation> findByEvent(@Param("eventId") UUID eventId);

    /**
     * (event, language) pairs of published events not yet over that have no translation, an AI
     * translation of older content, or a failed one with attempts left. Soonest events first.
     */
    List<LocalEventTranslationTask> findPending(@Param("locales") List<String> locales,
                                                @Param("now") LocalDateTime now,
                                                @Param("maxAttempts") int maxAttempts,
                                                @Param("limit") int limit);

    /** Writes an AI translation; a MANUAL row is left untouched. */
    int upsertAi(@Param("t") LocalEventTranslation translation);

    /** Records a failed attempt; a MANUAL row is left untouched. */
    int upsertFailed(@Param("eventId") UUID eventId,
                     @Param("locale") String locale,
                     @Param("sourceHash") String sourceHash,
                     @Param("now") LocalDateTime now);

    int upsertManual(@Param("t") LocalEventTranslation translation);

    int delete(@Param("eventId") UUID eventId, @Param("locale") String locale);
}
