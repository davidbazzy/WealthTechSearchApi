package com.baz.searchapi.controller;

import com.baz.searchapi.config.TestMockMvcConfig;
import com.baz.searchapi.model.dto.ClientResponse;
import com.baz.searchapi.model.dto.DocumentResponse;
import com.baz.searchapi.model.dto.SearchResultItem;
import com.baz.searchapi.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SearchController.class)
@Import(TestMockMvcConfig.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchService searchService;

    // --- Prebuilt result fixtures ---

    private SearchResultItem clientResult() {
        return SearchResultItem.fromClient(new ClientResponse(
                UUID.randomUUID(), "John", "Doe", "john@example.com", "Senior advisor", null));
    }

    private SearchResultItem docResult() {
        return SearchResultItem.fromDocument(new DocumentResponse(
                UUID.randomUUID(), UUID.randomUUID(), "Utility Bill", "Bill content", LocalDateTime.now()), 0.75);
    }

    // --- Response structure ---

    @Test
    void search_responseIsArray() throws Exception {
        when(searchService.search(anyString())).thenReturn(List.of());

        mockMvc.perform(get("/search").param("q", "anything"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void search_noResults_returnsEmptyArray() throws Exception {
        when(searchService.search(anyString())).thenReturn(List.of());

        mockMvc.perform(get("/search").param("q", "xyznonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void search_multipleResults_allReturned() throws Exception {
        when(searchService.search(anyString())).thenReturn(List.of(clientResult(), docResult()));

        mockMvc.perform(get("/search").param("q", "finance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    // --- Client result format ---

    @Test
    void search_clientResult_hasCorrectFields() throws Exception {
        when(searchService.search(anyString())).thenReturn(List.of(clientResult()));

        mockMvc.perform(get("/search").param("q", "john"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("client"))
                .andExpect(jsonPath("$[0].first_name").value("John"))
                .andExpect(jsonPath("$[0].last_name").value("Doe"))
                .andExpect(jsonPath("$[0].email").value("john@example.com"))
                .andExpect(jsonPath("$[0].description").value("Senior advisor"));
    }

    @Test
    void search_clientResult_hasNoDocumentFields() throws Exception {
        when(searchService.search(anyString())).thenReturn(List.of(clientResult()));

        mockMvc.perform(get("/search").param("q", "john"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").doesNotExist())
                .andExpect(jsonPath("$[0].content").doesNotExist())
                .andExpect(jsonPath("$[0].client_id").doesNotExist());
    }

    // --- Document result format ---

    @Test
    void search_documentResult_hasCorrectFields() throws Exception {
        when(searchService.search(anyString())).thenReturn(List.of(docResult()));

        mockMvc.perform(get("/search").param("q", "utility bill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("document"))
                .andExpect(jsonPath("$[0].title").value("Utility Bill"))
                .andExpect(jsonPath("$[0].content").value("Bill content"))
                .andExpect(jsonPath("$[0].client_id").exists())
                .andExpect(jsonPath("$[0].created_at").exists());
    }

    @Test
    void search_documentResult_hasNoClientFields() throws Exception {
        when(searchService.search(anyString())).thenReturn(List.of(docResult()));

        mockMvc.perform(get("/search").param("q", "utility bill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].first_name").doesNotExist())
                .andExpect(jsonPath("$[0].last_name").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist());
    }

    @Test
    void search_documentResult_noRelevanceScoreInResponse() throws Exception {
        when(searchService.search(anyString())).thenReturn(List.of(docResult()));

        mockMvc.perform(get("/search").param("q", "utility bill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].relevance_score").doesNotExist());
    }

    // --- Query validation ---

    @Test
    void search_blankQuery_returns400() throws Exception {
        mockMvc.perform(get("/search").param("q", "   "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_missingQuery_returns400() throws Exception {
        mockMvc.perform(get("/search"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_queryIsTrimmmedBeforePassingToService() throws Exception {
        when(searchService.search("finance")).thenReturn(List.of());

        // Leading/trailing whitespace should be trimmed
        mockMvc.perform(get("/search").param("q", "  finance  "))
                .andExpect(status().isOk());
    }
}
