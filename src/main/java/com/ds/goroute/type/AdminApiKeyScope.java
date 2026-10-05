package com.ds.goroute.type;

/**
 * What an admin API key may be used for. The scope only narrows the paths a key is accepted on;
 * the key's owner still needs the matching RBAC permission.
 */
public enum AdminApiKeyScope {
    EVENTS_IMPORT("events:import");

    private final String code;

    AdminApiKeyScope(String code) {
        this.code = code;
    }

    /** The value stored in {@code admin_api_keys.scopes} and returned by the API. */
    public String code() {
        return code;
    }
}
