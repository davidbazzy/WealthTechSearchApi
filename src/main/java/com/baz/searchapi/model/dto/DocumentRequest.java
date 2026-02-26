package com.baz.searchapi.model.dto;

import jakarta.validation.constraints.NotBlank;

public record DocumentRequest(
        @NotBlank(message = "title is required")
        String title,

        @NotBlank(message = "content is required")
        String content
) {
}
