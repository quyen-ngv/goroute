package com.ds.goroute.type;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * What an admin API key may be used for. The scope only narrows the paths a key is accepted on;
 * the key's owner still needs the matching RBAC permission.
 */
public enum AdminApiKeyScope {
    EVENTS_IMPORT("events:import"),
    /** The place-linking agent: reads the unlinked queue and submits proposals. Cannot approve. */
    ACTIVITY_PLACES_PROPOSE("activity-places:propose"),
    /** The Telegram approver: approves or rejects proposals and dismisses activities. */
    ACTIVITY_PLACES_APPROVE("activity-places:approve");

    private final String code;

    AdminApiKeyScope(String code) {
        this.code = code;
    }

    /** The value stored in {@code admin_api_keys.scopes} and returned by the API. */
    public String code() {
        return code;
    }

    public static Optional<AdminApiKeyScope> fromCode(String code) {
        return Stream.of(values()).filter(scope -> scope.code.equals(code)).findFirst();
    }
}
