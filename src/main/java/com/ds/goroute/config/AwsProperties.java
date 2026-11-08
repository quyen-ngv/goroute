package com.ds.goroute.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "goroute.aws")
public class AwsProperties {
    private String accessKeyId;
    private String secretAccessKey;
    private String region;
    private String s3BucketName;
    private String cloudfrontDomain;
    private String endpoint;

    /**
     * Concurrent connections to S3/MinIO. Every photo upload holds one for the whole PUT, so
     * this stays above goroute.upload.max-concurrent-uploads (16) plus the image jobs
     * (migration 10, archive 4): an upload waiting for a connection here fails as a 500.
     */
    private int s3MaxConnections = 48;

    /** How long an upload waits for a free connection before it fails. */
    private Duration s3ConnectionAcquisitionTimeout = Duration.ofSeconds(20);

    private Duration s3ConnectionTimeout = Duration.ofSeconds(5);

    private Duration s3SocketTimeout = Duration.ofSeconds(60);
}
