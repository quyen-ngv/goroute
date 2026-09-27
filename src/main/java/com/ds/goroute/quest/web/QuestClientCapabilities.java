package com.ds.goroute.quest.web;

import java.util.Arrays;
import java.util.Locale;

/**
 * What the calling app can play, from its {@code X-Quest-Capabilities} header: a comma-separated
 * list such as {@code ar_object}. An app that predates a capability does not send it, and the
 * server then serves that content in a form the app knows (an AR_OBJECT checkpoint as ARRIVE).
 */
public final class QuestClientCapabilities {

    public static final String HEADER = "X-Quest-Capabilities";
    static final String AR_OBJECT = "ar_object";

    private QuestClientCapabilities() {
    }

    public static boolean arObjects(String header) {
        return header != null && Arrays.stream(header.split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(AR_OBJECT::equals);
    }
}
