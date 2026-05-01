package com.mobility.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * FIX: Spring Security was blocking ALL requests with 401/403 by default
 * because no SecurityFilterChain bean was defined.
 *
 * This config:
 *  1. Permits all API requests (for local dev — add JWT later for prod)
 *  2. Allows WebSocket connections from the React frontend
 *  3. Configures CORS so localhost:5173 (Vite) can call localhost:8080
 *
 * Place at: src/main/java/com/mobility/config/SecurityConfig.java
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF — REST APIs use stateless tokens, not cookies
                .csrf(AbstractHttpConfigurer::disable)

                // Enable CORS with our custom config below
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Permit all requests — auth will be added in production
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // FIX: Allow the React dev server and any local origin
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:5173",   // Vite dev server
                "http://localhost:3000",   // Create React App (if used)
                "http://localhost:*"       // any other local port
        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}