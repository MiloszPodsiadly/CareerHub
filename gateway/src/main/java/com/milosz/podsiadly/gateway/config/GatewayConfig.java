package com.milosz.podsiadly.gateway.config;

import com.milosz.podsiadly.gateway.jwt.JwtAuthServletFilter;
import com.milosz.podsiadly.gateway.jwt.GwJwtProperties;
import com.milosz.podsiadly.gateway.jwt.PublicPathsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@EnableConfigurationProperties({GwJwtProperties.class, PublicPathsProperties.class})
public class GatewayConfig {


    @Bean
    public FilterRegistrationBean<JwtAuthServletFilter> jwtFilterRegistration(JwtAuthServletFilter filter) {
        FilterRegistrationBean<JwtAuthServletFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(filter);
        reg.setUrlPatterns(java.util.List.of("/api/*"));
        reg.setOrder(-100);
        return reg;
    }
}
