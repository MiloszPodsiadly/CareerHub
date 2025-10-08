package com.milosz.podsiadly.gateway.config;


import com.milosz.podsiadly.gateway.jwt.JwtAuthServletFilter;
import com.milosz.podsiadly.gateway.jwt.PublicPathsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    //  private final JwtAuthServletFilter jwtFilter;
    private final PublicPathsProperties publicPaths;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        String[] publicPatterns = publicPaths.getPublicPaths().toArray(String[]::new);

        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicPatterns).permitAll()   // /api/auth/**, /api/public/**, /api/jobs**, /api/ingest**, /actuator/**
                        .anyRequest().authenticated()
                );
//      .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
