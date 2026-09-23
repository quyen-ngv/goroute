package com.ds.goroute.config;

import com.ds.goroute.config.filter.ApiKeyAuthenticationFilter;
import com.ds.goroute.config.filter.InternalApiAuthenticationFilter;
import com.ds.goroute.config.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.DispatcherType;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({InternalApiProperties.class, CorsProperties.class, JwtProperties.class})
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    private final InternalApiAuthenticationFilter internalApiAuthenticationFilter;
    private final CorsProperties corsProperties;
    private final ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;
    private final ApiAccessDeniedHandler apiAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(apiAuthenticationEntryPoint)
                        .accessDeniedHandler(apiAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // SseEmitter completion/progress callbacks use an
                        // internal ASYNC servlet dispatch after the original
                        // JWT-authenticated request has already started the
                        // response. Re-authenticating that dispatch as a new
                        // request causes a late 403 after the SSE response is
                        // committed; the initial endpoint remains protected.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/v1/api/auth/**").permitAll()
                        .requestMatchers("/v1/api/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/api/location-images/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/api/weather").permitAll()
                        .requestMatchers("/v1/api/city-stories/feed").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/api/location-images/*/stories").permitAll()
                        // Must precede the public GET rule below: this one can expose INACTIVE places.
                        .requestMatchers(HttpMethod.GET, "/v1/api/places/detail-refresh-candidates")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_API_KEY")
                        .requestMatchers(HttpMethod.GET, "/v1/api/places/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/api/places/import", "/v1/api/places/import/batch")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_API_KEY")
                        .requestMatchers(HttpMethod.PUT, "/v1/api/places/*")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_API_KEY")
                        .requestMatchers("/v1/api/places/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/v1/api/place-reviews/**")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_API_KEY")
                        .requestMatchers(HttpMethod.POST, "/v1/api/activity-bookings/*/add-to-trip").authenticated()
                        .requestMatchers("/v1/api/activity-bookings/**").permitAll()
                        .requestMatchers("/v1/api/foods/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/api/reviews/places/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/api/reviews/places/*/score").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/api/reviews/activity-bookings/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/api/reviews/activity-bookings/*/score").permitAll()
                        // Console login and self-serve partner sign-up are the only public admin-auth routes.
                        .requestMatchers(HttpMethod.POST, "/v1/api/admin/auth/login",
                                "/v1/api/admin/auth/partner-register").permitAll()
                        .requestMatchers("/v1/api/admin/auth/session", "/v1/api/account/password").authenticated()
                        .requestMatchers(HttpMethod.POST, "/v1/api/admin/places/import", "/v1/api/admin/places/import/batch")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_API_KEY")
                        .requestMatchers(HttpMethod.PUT, "/v1/api/admin/places/*")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_API_KEY")
                        .requestMatchers("/v1/api/admin/**").hasAuthority("ROLE_ADMIN")
                        // Self-serve organization creation: the caller is an ordinary signed-in user who
                        // only *becomes* a partner (AdminMapper.isPartnerUser) once the organization exists,
                        // so this exact method+path must be matched BEFORE the /v1/api/partner/** rule
                        // below, which would otherwise demand ROLE_PARTNER the caller cannot hold yet.
                        .requestMatchers(HttpMethod.POST, "/v1/api/partner/organizations").authenticated()
                        // The listing wizard has the same shape of problem: it is where an ordinary
                        // account becomes a partner, so requiring ROLE_PARTNER to reach it would lock
                        // the only door that grants the role. Object-level checks live in the service.
                        .requestMatchers("/v1/api/partner-onboarding/**").authenticated()
                        .requestMatchers("/v1/api/partner/**").hasAnyAuthority("ROLE_PARTNER", "ROLE_ADMIN")
                        .requestMatchers("/v1/api/internal/**").hasAuthority("ROLE_INTERNAL")
                        .requestMatchers("/v1/api/notifications/admin/**").hasAuthority("ROLE_ADMIN")
                        // A shared collection is meant to be openable by anyone holding the
                        // link. Only this one method and path is public; every other
                        // collection route stays authenticated, and un-publishing clears
                        // the slug so an old link resolves to nothing.
                        .requestMatchers(HttpMethod.GET, "/v1/api/collections/shared/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/api/checkins/feed").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/api/checkins/places/*",
                                "/v1/api/checkins/locations/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/api/contributions/check").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/api/contributions/places/*/contributors").permitAll()
                        .requestMatchers("/share/**").permitAll()
                        .requestMatchers("/goroute/share/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/readiness", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/health").permitAll()
                        // The public Ondetour page is the only static asset left; the
                        // admin consoles now live in the separate React app.
                        .requestMatchers(
                                "/ondetour.html",
                                "/goroute/ondetour.html",
                                "/ondetour",
                                "/ondetour/*",
                                "/goroute/ondetour",
                                "/goroute/ondetour/*"
                        ).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(apiKeyAuthenticationFilter, JwtAuthenticationFilter.class)
                .addFilterBefore(internalApiAuthenticationFilter, ApiKeyAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(corsProperties.getAllowedOriginPatterns());
        configuration.setAllowedMethods(corsProperties.getAllowedMethods());
        configuration.setAllowedHeaders(corsProperties.getAllowedHeaders());
        configuration.setMaxAge(corsProperties.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new LengthCheckedBCryptPasswordEncoder();
    }

    /**
     * BCrypt has always ignored everything past the 72nd byte of a password. Spring
     * Security 6.4.4 stopped ignoring it silently and throws from {@code encode} instead
     * (CVE-2025-22228), which reaches the client as a 500 the first time somebody picks a
     * long passphrase — 25 accented Vietnamese characters are already 75 bytes. Answer with
     * the ordinary validation error instead.
     *
     * <p>Only {@code encode} is guarded. {@code matches} still accepts any length, so an
     * account whose password was set before this change keeps logging in exactly as it did.
     */
    static final class LengthCheckedBCryptPasswordEncoder extends BCryptPasswordEncoder {

        private static final int MAX_BYTES = 72;

        LengthCheckedBCryptPasswordEncoder() {
            super(12);
        }

        @Override
        public String encode(CharSequence rawPassword) {
            if (rawPassword != null
                    && rawPassword.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
                throw new BusinessException(ErrorConstant.BAD_REQUEST,
                        "Password must be at most " + MAX_BYTES + " bytes long");
            }
            return super.encode(rawPassword);
        }
    }
}
