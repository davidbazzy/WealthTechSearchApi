package com.baz.searchapi.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Adds the X-Api-Key header to every MockMvc request so that ApiKeyFilter
 * does not reject requests during unit and integration tests.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestMockMvcConfig {

    @Bean
    MockMvcBuilderCustomizer apiKeyHeader() {
        return builder -> builder.defaultRequest(get("/").header("X-Api-Key", "test-key"));
    }
}
