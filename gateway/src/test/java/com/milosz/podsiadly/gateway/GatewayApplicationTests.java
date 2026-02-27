package com.milosz.podsiadly.gateway;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "eureka.client.enabled=false",
                "eureka.client.register-with-eureka=false",
                "eureka.client.fetch-registry=false",
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.gateway.server.webmvc.discovery.locator.enabled=false",
                "spring.cloud.gateway.server.webmvc.proxy-exchange=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
                "spring.mustache.check-template-location=false"
        }
)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
public abstract class GatewayApplicationTests {

    private static final String TEST_JWT_SECRET =
            "not-a-secret-not-a-secret-not-a-secret-not-a-secret-123456";

    private static final String TEST_JWT_ISSUER = "test-issuer";

    @Container
    protected static final MockServerContainer backendMock =
            new MockServerContainer(DockerImageName.parse("mockserver/mockserver:5.15.0"));

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("auth.jwt.secret", () -> TEST_JWT_SECRET);
        registry.add("auth.jwt.issuer", () -> TEST_JWT_ISSUER);
        registry.add("spring.cloud.gateway.server.webmvc.routes[0].id", () -> "backend-mock");
        registry.add("spring.cloud.gateway.server.webmvc.routes[0].uri", backendMock::getEndpoint);
        registry.add("spring.cloud.gateway.server.webmvc.routes[0].predicates[0]", () -> "Path=/api/**");
    }

}