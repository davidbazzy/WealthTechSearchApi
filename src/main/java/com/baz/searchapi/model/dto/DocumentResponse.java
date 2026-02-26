package com.baz.searchapi.model.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID clientId,
        String title,
        String content,
        LocalDateTime createdAt
) {
}
