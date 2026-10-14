package com.ds.goroute.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Password-login lockout (epic 01). After {@code maxFailures} wrong passwords for one account,
 * password sign-in to that account is refused until {@code lockDuration} has passed since the
 * last failure. Per-IP limits live in nginx; this one stops guessing spread across addresses.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "goroute.security.login-lockout")
public class LoginLockoutProperties {

    private boolean enabled = true;

    @Min(3)
    @Max(100)
    private int maxFailures = 5;

    @NotNull
    private Duration lockDuration = Duration.ofMinutes(15);

    /** Accounts tracked at once; bounds memory when someone sprays many usernames. */
    @Min(1_000)
    @Max(1_000_000)
    private int maxTrackedAccounts = 100_000;
}
