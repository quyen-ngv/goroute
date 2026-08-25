package com.ds.goroute.config;

import com.ds.goroute.config.filter.ApiKeyAuthenticationFilter;
import com.ds.goroute.config.filter.InternalApiAuthenticationFilter;
import com.ds.goroute.config.filter.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigStartupTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withUserConfiguration(SecurityConfig.class)
            .withPropertyValues(
                    "application.security.jwt.secret-key=test-only-security-config-secret-key-1234567890")
            .withBean(JwtAuthenticationFilter.class, () -> mock(JwtAuthenticationFilter.class))
            .withBean(ApiKeyAuthenticationFilter.class, () -> mock(ApiKeyAuthenticationFilter.class))
            .withBean(InternalApiAuthenticationFilter.class, () -> mock(InternalApiAuthenticationFilter.class))
            .withBean(ApiAuthenticationEntryPoint.class, () -> mock(ApiAuthenticationEntryPoint.class))
            .withBean(ApiAccessDeniedHandler.class, () -> mock(ApiAccessDeniedHandler.class))
            .withBean("mvcHandlerMappingIntrospector", HandlerMappingIntrospector.class,
                    HandlerMappingIntrospector::new);

    @Test
    void createsSecurityFilterChainWithCustomFilterOrder() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SecurityFilterChain.class);
        });
    }
}
