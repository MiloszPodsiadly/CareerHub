package com.milosz.podsiadly.gateway.unit;

import com.milosz.podsiadly.gateway.jwt.JwtRequestWrapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class JwtRequestWrapperTests {

    @Test
    void overrides_authorization_header() {
        MockHttpServletRequest base = new MockHttpServletRequest();
        base.addHeader(HttpHeaders.AUTHORIZATION, "Bearer old");

        JwtRequestWrapper wrapper = new JwtRequestWrapper(base, "new-token");
        assertThat(wrapper.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer new-token");
    }

    @Test
    void overrides_authorization_headers_enumeration() {
        MockHttpServletRequest base = new MockHttpServletRequest();
        base.addHeader(HttpHeaders.AUTHORIZATION, "Bearer old");

        JwtRequestWrapper wrapper = new JwtRequestWrapper(base, "new-token");
        List<String> headers = java.util.Collections.list(wrapper.getHeaders(HttpHeaders.AUTHORIZATION));
        assertThat(headers).containsExactly("Bearer new-token");
    }
}
