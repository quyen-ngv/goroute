package com.ds.goroute.enums;

/** Where a consent answer was given (V278 {@code user_consents.source}). */
public enum ConsentSource {
    /** The full-screen consent step shown before the app opens. */
    CONSENT_SCREEN,
    /** A switch in Settings, the Pengoo memory panel or another settings surface. */
    SETTINGS
}
