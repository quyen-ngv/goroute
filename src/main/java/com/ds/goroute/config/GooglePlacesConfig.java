package com.ds.goroute.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GooglePlacesProperties.class)
public class GooglePlacesConfig {
}
