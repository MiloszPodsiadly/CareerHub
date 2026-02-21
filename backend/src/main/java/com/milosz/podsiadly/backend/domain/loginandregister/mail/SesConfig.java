package com.milosz.podsiadly.backend.domain.loginandregister.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

@Configuration
@ConditionalOnProperty(name = "app.mailer.enabled", havingValue = "true", matchIfMissing = true)
public class SesConfig {

    @Bean
    SesV2Client sesV2Client(SesMailProperties props) {
        if (props.getRegion() == null || props.getRegion().isBlank()) {
            throw new IllegalStateException("app.mailer.aws.region must be set");
        }

        return SesV2Client.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .region(Region.of(props.getRegion()))
                .build();
    }
}
