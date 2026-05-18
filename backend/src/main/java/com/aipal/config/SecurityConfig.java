package com.aipal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private List<String> allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/agents/**").permitAll()
                .requestMatchers("/api/v1/agent-config/**").permitAll()
                .requestMatchers("/api/v1/agent-quality/**").permitAll()
                .requestMatchers("/api/v1/datasets/**").permitAll()
                .requestMatchers("/api/v1/models/**").permitAll()
                .requestMatchers("/api/v1/model-training/**").permitAll()
                .requestMatchers("/api/v1/registry/agents/**").permitAll()
                .requestMatchers("/api/v1/monitor/**").permitAll()
                .requestMatchers("/api/v1/agent-graph/**").permitAll()
                .requestMatchers("/api/v1/heartbeat/**").permitAll()
                .requestMatchers("/api/v1/business-dashboard/**").permitAll()
                .requestMatchers("/api/v1/billing/**").permitAll()
                .requestMatchers("/api/v1/alerts/**").permitAll()
                .requestMatchers("/api/v1/audit-logs/**").permitAll()
                .requestMatchers("/api/v1/customers/**").permitAll()
                .requestMatchers("/api/v1/invocations/**").permitAll()
                .requestMatchers("/api/v1/automation/**").permitAll()
                .requestMatchers("/api/v1/code-quality/**").permitAll()
                .requestMatchers("/api/v1/rag/**").permitAll()
                .requestMatchers("/api/v1/skills/**").permitAll()
                .requestMatchers("/api/v1/user-memories/**").permitAll()
                .requestMatchers("/doc.html", "/webjars/**", "/swagger-resources/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
