package com.ds.goroute.type;

/**
 * Why a person lost a trip, carried as {@code reason} on {@code trip.accessRevoked}.
 *
 * <p>Part of the WebSocket contract. Clients must treat an unknown reason like {@code removed}.
 */
public enum TripAccessRevokedReason {
    DELETED("deleted"),
    REMOVED("removed"),
    LEFT("left"),
    DECLINED("declined");

    private final String wireValue;

    TripAccessRevokedReason(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
