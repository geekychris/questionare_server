package com.example.questionnaire.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@EnableWebSecurity
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                // Vaadin endpoints
                .requestMatchers(new AntPathRequestMatcher("/VAADIN/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/vaadinServlet/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/frontend/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/frontend-es6/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/frontend-es5/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/resources/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/sw.js")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/sw-runtime-resources-precache.js")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/manifest.webmanifest")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/offline.html")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/icons/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/images/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/styles/**")).permitAll()
                // App routes
                .requestMatchers(new AntPathRequestMatcher("/")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/questionnaire/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/api/**")).permitAll()
                // Default rule
                .anyRequest().permitAll()
            )
            .csrf(csrf -> csrf
                .ignoringRequestMatchers(new AntPathRequestMatcher("/api/**"))
            )
            .headers(headers -> 
                headers.frameOptions(frameOptions -> frameOptions.sameOrigin())
            );
            
        return http.build();
    }
}
