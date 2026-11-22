package com.ds.goroute.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The calling app's build number, from the {@code X-App-Build} header the app has sent on every
 * request since 2026-09. For answers that must differ for app builds that cannot handle them.
 */
public final class AppBuildGate {

    /** 1.0.19: the first build whose router no longer loops on /profile-onboarding. */
    public static final int PROFILE_ONBOARDING_FIXED = 22;

    private static final String HEADER = "X-App-Build";

    private AppBuildGate() {
    }

    /** True when the current request comes from app build {@code build} or later. */
    public static boolean atLeast(int build) {
        Integer current = currentBuild();
        return current != null && current >= build;
    }

    /**
     * What to send as {@code onboardingCompleted}: the stored value for fixed builds, and
     * {@code true} for older builds (and callers without the header), which loop on the
     * profile setup screen and land on "page not found".
     */
    public static boolean onboardingCompletedForApp(Boolean stored) {
        if (!atLeast(PROFILE_ONBOARDING_FIXED)) {
            return true;
        }
        return Boolean.TRUE.equals(stored);
    }

    private static Integer currentBuild() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String header = request.getHeader(HEADER);
        if (header == null || !header.matches("\\d{1,9}")) {
            return null;
        }
        return Integer.valueOf(header);
    }
}
