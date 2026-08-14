package com.ds.goroute.config;

import com.ds.goroute.thirdparty.ai.AiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration(proxyBeanMethods = false)
@Slf4j
public class AiClientFallbackConfig {

    @Bean
    @ConditionalOnMissingBean(AiClient.class)
    AiClient unavailableAiClient(@Value("${ai.provider:deepseek}") String provider) {
        log.warn("Unsupported AI provider '{}'; AI trip ranking will use the local fallback", provider);
        return (systemPrompt, userPrompt) -> Optional.empty();
    }
}
