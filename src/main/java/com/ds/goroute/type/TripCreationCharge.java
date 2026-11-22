package com.ds.goroute.type;

/** How a trip creation pays its AI_CREDIT.TRIP_CREATION_COST. */
public enum TripCreationCharge {
    /** Refused when the balance is short: manual create, clone, Pengoo's trip card. */
    REQUIRE_BALANCE,
    /**
     * May go below zero: saving an AI draft. Its balance was checked when the job started, and a
     * refusal here would leave the user with the AI bill paid and nothing saved.
     */
    ALLOW_OVERDRAFT
}
