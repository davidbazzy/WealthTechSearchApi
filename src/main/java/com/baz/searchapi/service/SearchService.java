package com.baz.searchapi.service;

import com.baz.searchapi.model.dto.SearchResultItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SearchService {

    private final ClientService clientService;
    private final DocumentService documentService;

    public SearchService(ClientService clientService, DocumentService documentService) {
        this.clientService = clientService;
        this.documentService = documentService;
    }

    public List<SearchResultItem> search(String query) {
        List<SearchResultItem> results = new ArrayList<>();
        results.addAll(clientService.searchClients(query));
        results.addAll(documentService.searchDocuments(query));
        return results;
    }
}
