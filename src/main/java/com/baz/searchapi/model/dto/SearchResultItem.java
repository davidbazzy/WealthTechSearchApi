package com.baz.searchapi.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public sealed interface SearchResultItem permits SearchResultItem.ClientResult, SearchResultItem.DocumentResult {

    String type();
    UUID id();

    static ClientResult fromClient(ClientResponse clientResponse) {
        return ClientResult.from(clientResponse);
    }

    static DocumentResult fromDocument(DocumentResponse documentResponse, double score) {
        return DocumentResult.from(documentResponse, score);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ClientResult(
            String type,
            UUID id,
            String firstName,
            String lastName,
            String email,
            String description,
            List<String> socialLinks
    ) implements SearchResultItem {

        static ClientResult from(ClientResponse clientResponse) {
            return new ClientResult("client", clientResponse.id(), clientResponse.firstName(), clientResponse.lastName(),
                    clientResponse.email(), clientResponse.description(), clientResponse.socialLinks());
        }
    }

    record DocumentResult(
            String type,
            UUID id,
            UUID clientId,
            String title,
            String content,
            LocalDateTime createdAt,
            double score
    ) implements SearchResultItem {

        static DocumentResult from(DocumentResponse documentResponse, double score) {
            return new DocumentResult("document", documentResponse.id(), documentResponse.clientId(), documentResponse.title(),
                    documentResponse.content(), documentResponse.createdAt(), score);
        }
    }
}
