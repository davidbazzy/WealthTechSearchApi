package com.baz.searchapi.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchResultItem(
        String type,
        UUID id,
        String firstName,
        String lastName,
        String email,
        String description,
        List<String> socialLinks,
        UUID clientId,
        String title,
        String content,
        LocalDateTime createdAt
) {

    public static SearchResultItem fromClient(ClientResponse c) {
        return new SearchResultItem("client", c.id(), c.firstName(), c.lastName(),
                c.email(), c.description(), c.socialLinks(), null, null, null, null);
    }

    public static SearchResultItem fromDocument(DocumentResponse d) {
        return new SearchResultItem("document", d.id(), null, null, null, null, null,
                d.clientId(), d.title(), d.content(), d.createdAt());
    }
}
