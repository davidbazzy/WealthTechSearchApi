package com.baz.searchapi.controller;

import com.baz.searchapi.model.dto.SearchResultItem;
import com.baz.searchapi.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@Tag(name = "Search", description = "Semantic search across clients and documents")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    @Operation(summary = "Search across clients and documents",
            description = "Clients are matched by substring on name/email/description. "
                    + "Documents are matched by keyword and semantic similarity using embeddings.")
    @ApiResponse(responseCode = "200", description = "Search results")
    @ApiResponse(responseCode = "400", description = "Missing or blank query")
    public List<SearchResultItem> search(
            @Parameter(description = "Search query", example = "address proof")
            @RequestParam String q) {
        if (q == null || q.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Query parameter 'q' is required and must not be blank");
        }
        return searchService.search(q.trim());
    }
}
