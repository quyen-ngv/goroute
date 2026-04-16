package com.ds.goroute.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "goroute.aws")
public class AwsProperties {
    private String accessKeyId;
    private String secretAccessKey;
    private String region;
    private String s3BucketName;
    private String cloudfrontDomain;
}
