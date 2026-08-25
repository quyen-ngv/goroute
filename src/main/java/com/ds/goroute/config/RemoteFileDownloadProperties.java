package com.ds.goroute.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Operational limits for fetching a user-supplied remote file before it is stored in S3.
 */
@Data
@Configuration
@Validated
@ConfigurationProperties(prefix = "goroute.remote-file-download")
public class RemoteFileDownloadProperties {

    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(10);

    @NotNull
    private Duration readTimeout = Duration.ofSeconds(30);

    @NotNull
    private DataSize maxBytes = DataSize.ofMegabytes(25);

    @Min(0)
    @Max(5)
    private int maxRedirects = 3;

    @AssertTrue(message = "remote file download connectTimeout must be between 1 second and 1 minute")
    public boolean isConnectTimeoutValid() {
        return within(connectTimeout, Duration.ofSeconds(1), Duration.ofMinutes(1));
    }

    @AssertTrue(message = "remote file download readTimeout must be between 1 second and 5 minutes")
    public boolean isReadTimeoutValid() {
        return within(readTimeout, Duration.ofSeconds(1), Duration.ofMinutes(5));
    }

    @AssertTrue(message = "remote file download maxBytes must be between 1 MB and 100 MB")
    public boolean isMaxBytesValid() {
        return maxBytes != null
                && maxBytes.toBytes() >= DataSize.ofMegabytes(1).toBytes()
                && maxBytes.toBytes() <= DataSize.ofMegabytes(100).toBytes();
    }

    private boolean within(Duration value, Duration minimum, Duration maximum) {
        return value != null && value.compareTo(minimum) >= 0 && value.compareTo(maximum) <= 0;
    }
}
