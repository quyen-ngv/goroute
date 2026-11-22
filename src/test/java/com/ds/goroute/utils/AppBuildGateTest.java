package com.ds.goroute.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class AppBuildGateTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static void callFromBuild(String build) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (build != null) {
            request.addHeader("X-App-Build", build);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void fixedBuildsGetTheStoredOnboardingFlag() {
        callFromBuild("22");
        assertThat(AppBuildGate.onboardingCompletedForApp(false)).isFalse();
        assertThat(AppBuildGate.onboardingCompletedForApp(null)).isFalse();
        assertThat(AppBuildGate.onboardingCompletedForApp(true)).isTrue();

        callFromBuild("24");
        assertThat(AppBuildGate.onboardingCompletedForApp(false)).isFalse();
    }

    @Test
    void buildsBefore1019AreAlwaysToldOnboardingIsDone() {
        callFromBuild("21");
        assertThat(AppBuildGate.onboardingCompletedForApp(false)).isTrue();
    }

    @Test
    void aMissingOrGarbageHeaderCountsAsAnOldBuild() {
        callFromBuild(null);
        assertThat(AppBuildGate.onboardingCompletedForApp(false)).isTrue();

        callFromBuild("1.0.19");
        assertThat(AppBuildGate.onboardingCompletedForApp(false)).isTrue();

        RequestContextHolder.resetRequestAttributes();
        assertThat(AppBuildGate.onboardingCompletedForApp(false)).isTrue();
    }
}
