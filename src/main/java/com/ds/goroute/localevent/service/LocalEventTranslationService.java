package com.ds.goroute.localevent.service;

import com.ds.goroute.config.filter.AcceptLanguageFilter;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.localevent.domain.LocalEvent;
import com.ds.goroute.localevent.domain.LocalEventTranslation;
import com.ds.goroute.localevent.domain.LocalEventTranslationTask;
import com.ds.goroute.localevent.dto.LocalEventDetailResponse;
import com.ds.goroute.localevent.dto.LocalEventOriginalText;
import com.ds.goroute.localevent.dto.LocalEventTranslationRequest;
import com.ds.goroute.localevent.dto.LocalEventTranslationResponse;
import com.ds.goroute.localevent.persistence.LocalEventRepository;
import com.ds.goroute.localevent.persistence.LocalEventTranslationRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.thirdparty.ai.AiCallScope;
import com.ds.goroute.thirdparty.ai.AiClient;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.TranslationLocale;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * An event's free text in the reader's language (epic 17, V285).
 *
 * <p>Events are written by admins and crawlers, mostly in Vietnamese. The summary, description,
 * schedule note, price note and lunar date text of a published event are machine translated into
 * every app language by a background job, and again whenever the event's content changes. The
 * title, venue, address, organiser and performers are names: they are never translated, so they
 * keep matching the poster and the ticket seller's page.
 *
 * <p>The public detail returns the translation in the fields the app already shows, plus the
 * original text so a newer app can offer "show original". A translation edited on the console is
 * MANUAL and the job never overwrites it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalEventTranslationService {

    /** The app's languages; an event is translated into each one it is not written in. */
    static final List<String> TARGET_LOCALES = List.of(
            "en", "vi", "ko", "ja", "zh-CN", "zh-TW", "th", "ru", "hi", "fr", "de", "es");

    /** AI attempts for one (event, language) before it is left alone until the content changes. */
    static final int MAX_ATTEMPTS = 3;

    /** Model calls per job run. */
    static final int BATCH_SIZE = 12;

    /**
     * A longer description is left in its original language: one answer of the text model
     * cannot carry it whole, and half a description is worse than the original.
     */
    static final int DESCRIPTION_MAX_CHARS = 3000;

    private static final String SOURCE_AI = "AI";
    private static final String SOURCE_MANUAL = "MANUAL";
    private static final String STATUS_DONE = "DONE";

    private static final String SYSTEM_PROMPT = """
            You translate event listings for a travel app. Translate the JSON values you are given \
            into %s and answer with one JSON object that has exactly the keys "summary", \
            "description", "scheduleNote", "priceNote" and "lunarDateText". \
            Rules: translate meaning faithfully and naturally, add nothing and drop nothing; keep \
            line breaks; keep names of events, venues, people, organisations, brands and street \
            addresses exactly as written; keep numbers, dates, times, prices, currency codes and \
            URLs unchanged; a key whose value is null stays null. The "title" is context only: \
            do not translate it and do not return it.""";

    private final LocalEventRepository events;
    private final LocalEventTranslationRepository translations;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final BusinessConfigService config;

    /** Replaced in tests. */
    Clock clock = Clock.systemUTC();

    /**
     * Puts the reader's language into {@code response} when a finished translation exists for
     * it (or for the other Chinese script). Does nothing for a reader of the event's own language.
     */
    @Transactional(readOnly = true)
    public void localize(LocalEventDetailResponse response, LocalEvent event) {
        Optional<TranslationLocale> reader = TranslationLocale.fromCode(AcceptLanguageFilter.currentCode());
        if (reader.isEmpty() || sameLanguage(reader.get(), event.getLanguage())) {
            return;
        }
        Optional<LocalEventTranslation> found = findDone(event.getId(), reader.get());
        if (found.isEmpty()) {
            found = reader.get().sibling().flatMap(sibling -> findDone(event.getId(), sibling));
        }
        if (found.isEmpty()) {
            return;
        }
        LocalEventTranslation translation = found.get();
        LocalEventOriginalText original = LocalEventOriginalText.builder()
                .summary(response.getSummary())
                .description(response.getDescription())
                .scheduleNote(response.getScheduleNote())
                .priceNote(response.getPriceNote())
                .lunarDateText(response.getLunarDateText())
                .build();
        boolean replaced = false;
        if (hasText(translation.getSummary()) && hasText(response.getSummary())) {
            response.setSummary(translation.getSummary());
            replaced = true;
        }
        if (hasText(translation.getDescription()) && hasText(response.getDescription())) {
            response.setDescription(translation.getDescription());
            replaced = true;
        }
        if (hasText(translation.getScheduleNote()) && hasText(response.getScheduleNote())) {
            response.setScheduleNote(translation.getScheduleNote());
            replaced = true;
        }
        if (hasText(translation.getPriceNote()) && hasText(response.getPriceNote())) {
            response.setPriceNote(translation.getPriceNote());
            replaced = true;
        }
        if (hasText(translation.getLunarDateText()) && hasText(response.getLunarDateText())) {
            response.setLunarDateText(translation.getLunarDateText());
            replaced = true;
        }
        if (replaced) {
            response.setTranslated(true);
            response.setTranslationLocale(translation.getLocale());
            response.setTranslationSource(translation.getTranslationSource());
            response.setOriginal(original);
        }
    }

    /**
     * One job run: up to {@link #BATCH_SIZE} missing or stale (event, language) pairs, soonest
     * events first. Each pair is one model call and its own small write, so a failure costs
     * that pair only. Returns how many translations were written.
     */
    public int translatePending() {
        if (!config.getBoolean(BusinessConfigKey.EVENT_AUTO_TRANSLATE_ENABLED)) {
            return 0;
        }
        List<LocalEventTranslationTask> tasks =
                translations.findPending(TARGET_LOCALES, now(), MAX_ATTEMPTS, BATCH_SIZE);
        int written = 0;
        for (LocalEventTranslationTask task : tasks) {
            if (translateOne(task.getEventId(), task.getLocale())) {
                written++;
            }
        }
        return written;
    }

    @Transactional(readOnly = true)
    public List<LocalEventTranslationResponse> list(UUID eventId) {
        LocalEvent event = requireExisting(eventId);
        return translations.findByEvent(eventId).stream()
                .map(translation -> LocalEventTranslationResponse.builder()
                        .locale(translation.getLocale())
                        .summary(translation.getSummary())
                        .description(translation.getDescription())
                        .scheduleNote(translation.getScheduleNote())
                        .priceNote(translation.getPriceNote())
                        .lunarDateText(translation.getLunarDateText())
                        .translationSource(translation.getTranslationSource())
                        .status(translation.getStatus())
                        .upToDate(Objects.equals(translation.getSourceHash(), event.getContentHash()))
                        .updatedAt(translation.getUpdatedAt())
                        .build())
                .toList();
    }

    /** An admin's own wording for one language; the job leaves it alone from now on. */
    @Transactional
    public List<LocalEventTranslationResponse> saveManual(UUID eventId, String locale,
                                                          LocalEventTranslationRequest request) {
        LocalEvent event = requireExisting(eventId);
        String code = requireTargetLocale(locale);
        translations.saveManual(LocalEventTranslation.builder()
                .eventId(eventId)
                .locale(code)
                .summary(blankToNull(request.getSummary()))
                .description(blankToNull(request.getDescription()))
                .scheduleNote(blankToNull(request.getScheduleNote()))
                .priceNote(blankToNull(request.getPriceNote()))
                .lunarDateText(blankToNull(request.getLunarDateText()))
                .translationSource(SOURCE_MANUAL)
                .status(STATUS_DONE)
                .sourceHash(event.getContentHash())
                .updatedAt(now())
                .build());
        return list(eventId);
    }

    /** Drops one language's row, manual or not; the job then translates it again. */
    @Transactional
    public List<LocalEventTranslationResponse> reset(UUID eventId, String locale) {
        requireExisting(eventId);
        translations.delete(eventId, requireTargetLocale(locale));
        return list(eventId);
    }

    private boolean translateOne(UUID eventId, String locale) {
        LocalEvent event = events.findById(eventId).orElse(null);
        if (event == null) {
            return false;
        }
        try {
            if (!hasTranslatableText(event)) {
                // Only an over-long description: nothing to send, and nothing to retry.
                translations.saveAi(LocalEventTranslation.builder()
                        .eventId(eventId)
                        .locale(locale)
                        .sourceHash(event.getContentHash())
                        .updatedAt(now())
                        .build());
                return false;
            }
            LocalEventTranslation translation = askModel(event, locale);
            if (translation == null) {
                translations.saveFailed(eventId, locale, event.getContentHash(), now());
                return false;
            }
            translations.saveAi(translation);
            return true;
        } catch (RuntimeException exception) {
            log.warn("Could not translate event {} into {}: {}", eventId, locale, exception.getMessage());
            translations.saveFailed(eventId, locale, event.getContentHash(), now());
            return false;
        }
    }

    /** Whether the event has any text the model would be asked to translate. */
    private static boolean hasTranslatableText(LocalEvent event) {
        boolean shortDescription = hasText(event.getDescription())
                && event.getDescription().length() <= DESCRIPTION_MAX_CHARS;
        return shortDescription || hasText(event.getSummary()) || hasText(event.getScheduleNote())
                || hasText(event.getPriceNote()) || hasText(event.getLunarDateText());
    }

    /** Null when the model gave nothing usable. */
    private LocalEventTranslation askModel(LocalEvent event, String locale) {
        String description = event.getDescription() != null
                && event.getDescription().length() <= DESCRIPTION_MAX_CHARS ? event.getDescription() : null;
        ObjectNode input = objectMapper.createObjectNode();
        input.put("title", event.getTitle());
        input.put("summary", blankToNull(event.getSummary()));
        input.put("description", blankToNull(description));
        input.put("scheduleNote", blankToNull(event.getScheduleNote()));
        input.put("priceNote", blankToNull(event.getPriceNote()));
        input.put("lunarDateText", blankToNull(event.getLunarDateText()));

        String languageName = TranslationLocale.fromCode(locale)
                .map(TranslationLocale::displayName)
                .orElse(locale);
        Optional<String> answer = AiCallScope.call("EVENT_TRANSLATION", "TRANSLATE", null,
                event.getId().toString(),
                () -> aiClient.completeJson(SYSTEM_PROMPT.formatted(languageName), input.toString()));
        if (answer.isEmpty()) {
            return null;
        }
        JsonNode json;
        try {
            json = objectMapper.readTree(answer.get());
        } catch (Exception exception) {
            return null;
        }
        if (json == null || !json.isObject()) {
            return null;
        }
        LocalEventTranslation translation = LocalEventTranslation.builder()
                .eventId(event.getId())
                .locale(locale)
                // A field the event does not have is never filled from the model's answer.
                .summary(hasText(event.getSummary()) ? text(json, "summary", 600) : null)
                .description(description == null ? null : text(json, "description", 32000))
                .scheduleNote(hasText(event.getScheduleNote()) ? text(json, "scheduleNote", 600) : null)
                .priceNote(hasText(event.getPriceNote()) ? text(json, "priceNote", 600) : null)
                .lunarDateText(hasText(event.getLunarDateText()) ? text(json, "lunarDateText", 300) : null)
                .translationSource(SOURCE_AI)
                .status(STATUS_DONE)
                .sourceHash(event.getContentHash())
                .updatedAt(now())
                .build();
        boolean empty = translation.getSummary() == null && translation.getDescription() == null
                && translation.getScheduleNote() == null && translation.getPriceNote() == null
                && translation.getLunarDateText() == null;
        return empty ? null : translation;
    }

    private Optional<LocalEventTranslation> findDone(UUID eventId, TranslationLocale locale) {
        return translations.find(eventId, locale.code())
                .filter(translation -> STATUS_DONE.equals(translation.getStatus()));
    }

    /** Whether the reader's language is the one the event is written in (Vietnamese when unset). */
    private static boolean sameLanguage(TranslationLocale reader, String eventLanguage) {
        TranslationLocale source = TranslationLocale.fromCode(eventLanguage).orElse(TranslationLocale.DEFAULT);
        return source == reader;
    }

    private String requireTargetLocale(String locale) {
        return TranslationLocale.fromCode(locale)
                .map(TranslationLocale::code)
                .filter(TARGET_LOCALES::contains)
                .orElseThrow(() -> new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                        "Unknown translation language " + locale));
    }

    private LocalEvent requireExisting(UUID eventId) {
        return events.findById(eventId)
                .filter(event -> !Boolean.TRUE.equals(event.getIsDeleted()))
                .orElseThrow(() -> new BusinessException(ErrorConstant.LOCAL_EVENT_NOT_FOUND, "Event not found"));
    }

    private static String text(JsonNode json, String field, int maxLength) {
        JsonNode node = json.get(field);
        if (node == null || !node.isTextual()) {
            return null;
        }
        String value = node.asText().trim();
        if (value.isEmpty()) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
