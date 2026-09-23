package com.ds.goroute.type;

/**
 * At which level a check-in's location was proven.
 *
 * <p>Refines {@link CheckinVerificationStatus} rather than replacing it: every
 * {@code VERIFIED} row carries {@code PLACE} or {@code WARD}, every {@code UNVERIFIED}
 * row carries {@code NONE}. The two are kept as separate columns because everything
 * that already reads the status -- rewards, review trust, passport -- keeps its meaning,
 * and only the things that care about the difference look here.
 *
 * <p>The ladder stops at the ward. A province is too large to be evidence of anything.
 */
public enum CheckinVerificationScope {
    /** Inside the place's drawn area or verification radius. */
    PLACE,
    /** Not at the place, but provably inside a ward (xã/phường/đặc khu). */
    WARD,
    /** Nothing could be proven. */
    NONE
}
