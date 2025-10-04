package com.milosz.podsiadly.gateway.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "auth")
public class PublicPathsProperties {
    private List<String> publicPaths = new ArrayList<>();
}
