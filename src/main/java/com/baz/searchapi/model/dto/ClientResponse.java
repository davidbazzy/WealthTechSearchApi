package com.baz.searchapi.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String description,
        List<String> socialLinks
) {
    public ClientResponse {
        socialLinks = socialLinks != null ? List.copyOf(socialLinks) : List.of();
    }
}
