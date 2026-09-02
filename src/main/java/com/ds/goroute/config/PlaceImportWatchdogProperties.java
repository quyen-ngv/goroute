package com.ds.goroute.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "goroute.jobs.place-import-watchdog")
public class PlaceImportWatchdogProperties {

    /** How long a job may go without a worker callback before the worker is probed about it. */
    @NotNull
    private Duration silentAfter = Duration.ofMinutes(10);

    /** How long a job the worker cannot account for is kept open before it is closed. */
    @NotNull
    private Duration abandonAfter = Duration.ofMinutes(45);

    @AssertTrue(message = "place import watchdog silentAfter must be between 1 minute and 6 hours")
    public boolean isSilentAfterValid() {
        return within(silentAfter, Duration.ofMinutes(1), Duration.ofHours(6));
    }

    @AssertTrue(message = "place import watchdog abandonAfter must be at least silentAfter and at most 24 hours")
    public boolean isAbandonAfterValid() {
        return within(abandonAfter, Duration.ofMinutes(1), Duration.ofHours(24))
                && silentAfter != null
                && abandonAfter.compareTo(silentAfter) >= 0;
    }

    private boolean within(Duration value, Duration minimum, Duration maximum) {
        return value != null && value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0;
    }
}
