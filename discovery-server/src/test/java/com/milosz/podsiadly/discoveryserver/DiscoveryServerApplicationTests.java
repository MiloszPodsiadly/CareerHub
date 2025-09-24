package com.milosz.podsiadly.discoveryserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.cloud.netflix.eureka.server.EurekaServerAutoConfiguration"
})
class DiscoveryServerApplicationTests {

    @Test
    void contextLoads() {
    }

}
