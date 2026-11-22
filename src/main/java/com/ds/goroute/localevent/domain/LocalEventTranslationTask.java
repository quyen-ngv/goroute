package com.ds.goroute.localevent.domain;

import lombok.Data;

import java.util.UUID;

/** One (event, language) pair the translation job still has to write. */
@Data
public class LocalEventTranslationTask {

    private UUID eventId;
    private String locale;
}
