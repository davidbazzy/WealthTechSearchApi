package com.baz.searchapi.service;

import com.baz.searchapi.model.dto.SearchResultItem;
import com.baz.searchapi.model.entity.Chunk;
import com.baz.searchapi.model.entity.Client;
import com.baz.searchapi.model.entity.Document;
import com.baz.searchapi.repository.ChunkRepository;
import com.baz.searchapi.repository.DocumentRepository;
import com.baz.searchapi.repository.ClientRepository;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final ClientRepository clientRepository;
    private final ChunkRepository chunkRepository;
    private final DocumentRepository documentRepository;
    private final EmbeddingService embeddingService;
    private final ClientService clientService;
    private final DocumentService documentService;

    private static final double SIMILARITY_THRESHOLD = 0.4;

    public SearchService(ClientRepository clientRepository, ChunkRepository chunkRepository,
                         DocumentRepository documentRepository, EmbeddingService embeddingService,
                         ClientService clientService, DocumentService documentService) {
        this.clientRepository = clientRepository;
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.embeddingService = embeddingService;
        this.clientService = clientService;
        this.documentService = documentService;
    }

    public List<SearchResultItem> search(String query) {
        List<SearchResultItem> results = new ArrayList<>();

        results.addAll(searchClients(query));
        results.addAll(searchDocuments(query));

        return results;
    }

    private List<SearchResultItem> searchClients(String query) {
        return clientRepository.findAll().stream()
                .filter(client -> matchesClient(client, query))
                .map(client -> SearchResultItem.fromClient(clientService.toResponse(client)))
                .toList();
    }

    private boolean matchesClient(Client client, String query) {
        return Strings.CI.contains(client.getFirstName(), query)
                || Strings.CI.contains(client.getLastName(), query)
                || Strings.CI.contains(client.getEmail(), query)
                || Strings.CI.contains(client.getDescription(), query);
    }

    private List<SearchResultItem> searchDocuments(String query) {
        float[] queryEmbedding = embeddingService.embed(query);
        List<Chunk> allChunks = chunkRepository.findAll();

        // Track best score per document
        Map<UUID, Double> bestScores = new HashMap<>();
        for (Chunk chunk : allChunks) {
            float[] chunkEmbedding = EmbeddingService.toFloats(chunk.getEmbedding());
            double score = EmbeddingService.cosineSimilarity(queryEmbedding, chunkEmbedding);
            UUID docId = chunk.getDocument().getId();
            bestScores.merge(docId, score, Math::max);
        }

        // Keyword match: if query appears in title or content, ensure the document passes the threshold
        for (Document doc : documentRepository.findAll()) {
            UUID docId = doc.getId();
            if (Strings.CI.contains(doc.getTitle(), query)
                    || Strings.CI.contains(doc.getContent(), query)) {
                bestScores.merge(docId, SIMILARITY_THRESHOLD, Math::max);
            }
        }

        // Filter by threshold, sort by score descending
        return bestScores.entrySet().stream()
                .filter(e -> e.getValue() >= SIMILARITY_THRESHOLD)
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .map(e -> {
                    Document doc = documentRepository.findById(e.getKey()).orElseThrow();
                    double score = Math.round(e.getValue() * 10000.0) / 10000.0;
                    log.info("Document '{}' (id={}) matched query '{}' with relevance_score={}",
                            doc.getTitle(), doc.getId(), query, score);
                    return SearchResultItem.fromDocument(documentService.toResponse(doc));
                })
                .toList();
    }
}
