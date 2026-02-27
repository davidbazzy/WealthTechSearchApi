package com.baz.searchapi.service;

import com.baz.searchapi.model.dto.SearchResultItem;
import com.baz.searchapi.model.entity.Client;
import com.baz.searchapi.model.entity.Document;
import com.baz.searchapi.repository.ChunkRepository;
import com.baz.searchapi.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private EmbeddingService embeddingService;
    @Mock private ChunkRepository chunkRepository;

    @InjectMocks
    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        lenient().when(embeddingService.embed(anyString())).thenReturn(new float[384]);
        lenient().when(documentRepository.findDocumentIdsByKeyword(anyString())).thenReturn(List.of());
        lenient().when(chunkRepository.findTopDocumentsByEmbedding(anyString())).thenReturn(List.of());
    }

    // --- Semantic search ---

    @Test
    void searchDocuments_semanticMatch_aboveThreshold_isIncluded() {
        UUID docId = UUID.randomUUID();
        Document doc = stubDocument(docId, "Passport Copy");

        when(chunkRepository.findTopDocumentsByEmbedding(anyString()))
                .thenReturn(semanticRows(docId, 0.85));
        when(documentRepository.findAllById(anyIterable())).thenReturn(List.of(doc));

        List<SearchResultItem> results = documentService.searchDocuments("identity verification");

        assertEquals(1, results.size());
        assertEquals("document", results.getFirst().type());
        assertInstanceOf(SearchResultItem.DocumentResult.class, results.getFirst());
        assertEquals("Passport Copy", ((SearchResultItem.DocumentResult) results.getFirst()).title());
    }

    @Test
    void searchDocuments_semanticMatch_belowThreshold_isExcluded() {
        UUID docId = UUID.randomUUID();

        when(chunkRepository.findTopDocumentsByEmbedding(anyString()))
                .thenReturn(semanticRows(docId, 0.2));

        assertTrue(documentService.searchDocuments("identity verification").isEmpty());
    }

    @Test
    void searchDocuments_semanticMatch_combinedScoreAtThreshold_isIncluded() {
        UUID docId = UUID.randomUUID();
        Document doc = stubDocument(docId, "Tax Return");

        // semantic=0.36, no keyword → combined = 0.7 * 0.36 = 0.252, just above threshold 0.25.
        when(chunkRepository.findTopDocumentsByEmbedding(anyString()))
                .thenReturn(semanticRows(docId, 0.36));
        when(documentRepository.findAllById(anyIterable())).thenReturn(List.of(doc));

        assertEquals(1, documentService.searchDocuments("income").size());
    }

    // --- Keyword boost ---

    @Test
    void searchDocuments_keywordBoost_liftsDocumentAboveThreshold() {
        UUID docId = UUID.randomUUID();
        Document doc = stubDocument(docId, "Investment Portfolio");

        // Semantic score alone maps to 0.7 * 0.35 = 0.245 (below threshold 0.25).
        // Keyword normalises to 1.0 (only result), contributing 0.3 * 1.0 = 0.3.
        // Combined: 0.245 + 0.3 = 0.545 >= 0.25 → included.
        when(chunkRepository.findTopDocumentsByEmbedding(anyString()))
                .thenReturn(semanticRows(docId, 0.35));
        when(documentRepository.findDocumentIdsByKeyword(anyString()))
                .thenReturn(keywordRows(docId, 1.0));
        when(documentRepository.findAllById(anyIterable())).thenReturn(List.of(doc));

        assertEquals(1, documentService.searchDocuments("portfolio").size());
    }

    @Test
    void searchDocuments_keywordOnly_isIncluded() {
        UUID docId = UUID.randomUUID();
        Document doc = stubDocument(docId, "Utility Bill");

        // No semantic match. Keyword normalises to 1.0 → combined = 0.3 * 1.0 = 0.3 >= 0.25.
        when(documentRepository.findDocumentIdsByKeyword(anyString()))
                .thenReturn(keywordRows(docId, 0.8));
        when(documentRepository.findAllById(anyIterable())).thenReturn(List.of(doc));

        List<SearchResultItem> results = documentService.searchDocuments("utility");

        assertEquals(1, results.size());
        assertEquals("Utility Bill", ((SearchResultItem.DocumentResult) results.getFirst()).title());
    }

    // --- Sorting ---

    @Test
    void searchDocuments_multipleDocuments_sortedByScoreDescending() {
        UUID docIdHigh = UUID.randomUUID();
        UUID docIdLow = UUID.randomUUID();

        when(chunkRepository.findTopDocumentsByEmbedding(anyString()))
                .thenReturn(semanticRows(docIdLow, 0.5, docIdHigh, 0.9));
        when(documentRepository.findAllById(anyIterable()))
                .thenReturn(List.of(stubDocument(docIdHigh, "High Score Doc"), stubDocument(docIdLow, "Low Score Doc")));

        List<SearchResultItem> results = documentService.searchDocuments("query");

        assertEquals(2, results.size());
        assertEquals("High Score Doc", ((SearchResultItem.DocumentResult) results.get(0)).title());
        assertEquals("Low Score Doc", ((SearchResultItem.DocumentResult) results.get(1)).title());
    }

    @Test
    void searchDocuments_noMatches_returnsEmptyList() {
        assertTrue(documentService.searchDocuments("xyzunknown").isEmpty());
    }

    // --- Helpers ---

    private Document stubDocument(UUID id, String title) {
        Client client = new Client();
        client.setId(UUID.randomUUID());

        Document doc = new Document();
        doc.setId(id);
        doc.setTitle(title);
        doc.setContent("Some content about " + title);
        doc.setClient(client);
        return doc;
    }

    private static List<Object[]> semanticRows(Object... rows) {
        List<Object[]> result = new java.util.ArrayList<>();
        for (int i = 0; i < rows.length; i += 2) {
            result.add(new Object[]{rows[i], rows[i + 1]});
        }
        return result;
    }

    private static List<Object[]> keywordRows(Object... rows) {
        List<Object[]> result = new java.util.ArrayList<>();
        for (int i = 0; i < rows.length; i += 2) {
            result.add(new Object[]{rows[i], rows[i + 1]});
        }
        return result;
    }
}
