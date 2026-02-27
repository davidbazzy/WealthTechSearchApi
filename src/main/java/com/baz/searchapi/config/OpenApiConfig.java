package com.baz.searchapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /**
     * Tell springdoc to use SNAKE_CASE naming so Swagger UI matches the API response format.
     * Spring Boot 4 uses Jackson 3 (tools.jackson.*) for its context bean, so we create
     * a standalone Jackson 2 ObjectMapper here for springdoc's ModelResolver.
     */
    @Bean
    public ModelResolver modelResolver() {
        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return new ModelResolver(mapper);
    }

    @Bean
    public OpenApiCustomizer apiKeySecurityCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.components(new Components());
            }
            openApi.getComponents().addSecuritySchemes("X-Api-Key", new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name("X-Api-Key"));
            openApi.addSecurityItem(new SecurityRequirement().addList("X-Api-Key"));
        };
    }
}
