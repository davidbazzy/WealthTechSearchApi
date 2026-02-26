package com.baz.searchapi.model.dto;

import java.util.List;
import java.util.UUID;

public record ClientResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String description,
        List<String> socialLinks
) {
}
