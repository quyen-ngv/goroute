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
                        .requestMatchers("/v1/api/city-stories/feed").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/api/location-images/*/stories").permitAll()
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
                        .requestMatchers(HttpMethod.POST, "/v1/api/admin/auth/login").permitAll()
                        .requestMatchers("/v1/api/admin/auth/session", "/v1/api/account/password").authenticated()
                        .requestMatchers(HttpMethod.POST, "/v1/api/admin/places/import", "/v1/api/admin/places/import/batch")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_API_KEY")
                        .requestMatchers(HttpMethod.PUT, "/v1/api/admin/places/*")
                        .hasAnyAuthority("ROLE_ADMIN", "ROLE_API_KEY")
                        .requestMatchers("/v1/api/admin/**").hasAuthority("ROLE_ADMIN")
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
                        .requestMatchers(HttpMethod.GET, "/v1/api/guides/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/api/contributions/check").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/api/contributions/places/*/contributors").permitAll()
                        .requestMatchers("/share/**").permitAll()
                        .requestMatchers("/goroute/share/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/swagger-ui/**", "/api-docs/**", "/swagger-ui.html").permitAll()
                        // Static admin/share assets (PathPattern: ** must be last â€” no /**/*.css)
                        .requestMatchers(
                                "/admin-*.html",
                                "/admin-contributions.html",
                                "/*.html",
                                "/goroute-theme.css",
                                "/goroute-admin-utils.js",
                                "/goroute/admin-*.html",
                                "/goroute/goroute-theme.css",
                                "/goroute/goroute-admin-utils.js",
                                "/*.css",
                                "/*.js",
                                "/goroute/*.css",
                                "/goroute/*.js",
                                "/css/**",
                                "/js/**"
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
        return new BCryptPasswordEncoder(12);
    }
}
