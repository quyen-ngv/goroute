package com.ds.goroute.config.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "app.cache.local")
public class LocalCacheProperties {
    private boolean enable = true;
    private long timeoutSeconds = 300;
    private List<String> cacheNames = new ArrayList<>();
}
