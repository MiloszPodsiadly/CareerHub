package com.milosz.podsiadly.gateway.config;


import com.milosz.podsiadly.gateway.jwt.JwtAuthServletFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthServletFilter jwtFilter;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/events/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/salary/calculate").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/salary/report/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/favorites/*/*/status").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/jobs/mine").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/jobs").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/jobs/fast").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/jobs/count").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/jobs/all").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/jobs/by-external/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/jobs/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/jobs/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/jobs/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/jobs/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/applications").authenticated()
                        .requestMatchers("/api/applications/**").authenticated()
                        .requestMatchers("/api/job-drafts/**").authenticated()
                        .requestMatchers("/api/profile", "/api/profile/**").authenticated()
                        .requestMatchers("/api/favorites/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/ingest/url", "/api/ingest/sitemap").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
