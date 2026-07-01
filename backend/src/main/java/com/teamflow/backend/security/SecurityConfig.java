package com.teamflow.backend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpStatus;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // The STOMP handshake authenticates per-connection via the CONNECT frame
                        // (see StompAuthChannelInterceptor), so the HTTP upgrade itself is permitted.
                        // "/error" must be permitted too: an exception (@ResponseStatus, validation,
                        // etc.) triggers a container ERROR dispatch to /error on which the JWT filter
                        // does not run, so without this every error would be masked as a 401.
                        .requestMatchers("/api/auth/**", "/uploads/**", "/ws/**", "/error").permitAll()
                        .anyRequest().authenticated())
                // Return 401 (not the servlet default 403) when a request is unauthenticated, which is
                // the correct status for a missing/invalid bearer token on this stateless JSON API.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
