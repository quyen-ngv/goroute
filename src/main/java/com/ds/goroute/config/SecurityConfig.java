package com.ds.goroute.config;

import com.ds.goroute.config.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v1/api/auth/**").permitAll()
                        .requestMatchers("/v1/api/public/**").permitAll()
                        .requestMatchers("/v1/api/location-images/**").permitAll()
                        .requestMatchers("/v1/api/city-stories/feed").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/api/location-images/*/stories").permitAll()
                        .requestMatchers("/v1/api/places/**").permitAll()
                        .requestMatchers("/v1/api/place-reviews/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/api/activity-bookings/*/add-to-trip").authenticated()
                        .requestMatchers("/v1/api/activity-bookings/**").permitAll()
                        .requestMatchers("/v1/api/foods/**").permitAll()
                        .requestMatchers("/v1/api/admin/foods/**").permitAll()
                        .requestMatchers("/v1/api/admin/places/**").permitAll()
                        .requestMatchers("/v1/api/admin/place-import-mappings/**").permitAll()
                        .requestMatchers("/v1/api/admin/place-import-jobs/**").permitAll()
                        .requestMatchers(
                                "/v1/api/admin/contributions",
                                "/v1/api/admin/contributions/**"
                        ).permitAll()
                        .requestMatchers("/v1/api/internal/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/api/contributions/check").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/api/contributions/places/*/contributors").permitAll()
                        .requestMatchers("/share/**").permitAll()
                        .requestMatchers("/goroute/share/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
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
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
