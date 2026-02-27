package com.baz.searchapi.service;

import com.baz.searchapi.model.dto.ClientResponse;
import com.baz.searchapi.model.dto.DocumentResponse;
import com.baz.searchapi.model.dto.SearchResultItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock private ClientService clientService;
    @Mock private DocumentService documentService;

    @InjectMocks
    private SearchService searchService;

    @BeforeEach
    void setUp() {
        lenient().when(clientService.searchClients(anyString())).thenReturn(List.of());
        lenient().when(documentService.searchDocuments(anyString())).thenReturn(List.of());
    }

    @Test
    void search_noMatches_returnsEmptyList() {
        assertTrue(searchService.search("xyzunknown").isEmpty());
    }

    @Test
    void search_clientsAndDocuments_combinedInOrder() {
        var clientItem = SearchResultItem.fromClient(
                new ClientResponse(UUID.randomUUID(), "John", "Doe", "john@example.com", "Advisor", null));
        var docItem = SearchResultItem.fromDocument(
                new DocumentResponse(UUID.randomUUID(), UUID.randomUUID(), "Tax Return", "Content", LocalDateTime.now()), 0.75);

        when(clientService.searchClients(anyString())).thenReturn(List.of(clientItem));
        when(documentService.searchDocuments(anyString())).thenReturn(List.of(docItem));

        List<SearchResultItem> results = searchService.search("tax advisor");

        assertEquals(2, results.size());
        assertEquals("client", results.get(0).type());
        assertEquals("document", results.get(1).type());
    }

    @Test
    void search_onlyClients_returnsClientsOnly() {
        var clientItem = SearchResultItem.fromClient(
                new ClientResponse(UUID.randomUUID(), "Jane", "Smith", "jane@example.com", "Planner", null));

        when(clientService.searchClients(anyString())).thenReturn(List.of(clientItem));

        List<SearchResultItem> results = searchService.search("retirement");

        assertEquals(1, results.size());
        assertEquals("client", results.getFirst().type());
    }

    @Test
    void search_onlyDocuments_returnsDocumentsOnly() {
        var docItem = SearchResultItem.fromDocument(
                new DocumentResponse(UUID.randomUUID(), UUID.randomUUID(), "Passport Copy", "Content", LocalDateTime.now()), 0.75);

        when(documentService.searchDocuments(anyString())).thenReturn(List.of(docItem));

        List<SearchResultItem> results = searchService.search("identity");

        assertEquals(1, results.size());
        assertEquals("document", results.getFirst().type());
    }
}
