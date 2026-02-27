package com.baz.searchapi.service;

import com.baz.searchapi.model.dto.DocumentRequest;
import com.baz.searchapi.model.dto.DocumentResponse;
import com.baz.searchapi.model.dto.SearchResultItem;
import com.baz.searchapi.model.entity.Chunk;
import com.baz.searchapi.model.entity.Client;
import com.baz.searchapi.model.entity.Document;
import com.baz.searchapi.repository.ChunkRepository;
import com.baz.searchapi.repository.ClientRepository;
import com.baz.searchapi.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final double KEYWORD_WEIGHT        = 0.3;  // α: 30% keyword, 70% semantic
    private static final double SIMILARITY_THRESHOLD  = 0.25; // applied to the combined [0,1] score
    private static final double MIN_KEYWORD_NORMALISER = 0.1; // floor prevents inflation on weak matches

    private final DocumentRepository documentRepository;
    private final ClientRepository clientRepository;
    private final EmbeddingService embeddingService;
    private final ChunkRepository chunkRepository;

    public DocumentService(DocumentRepository documentRepository, ClientRepository clientRepository,
                           EmbeddingService embeddingService, ChunkRepository chunkRepository) {
        this.documentRepository = documentRepository;
        this.clientRepository = clientRepository;
        this.embeddingService = embeddingService;
        this.chunkRepository = chunkRepository;
    }

    public DocumentResponse createDocument(UUID clientId, DocumentRequest request) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Client not found"));

        if (documentRepository.findByClientIdAndTitleIgnoreCase(clientId, request.title()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A document with this title already exists for this client");
        }

        Document document = new Document();
        document.setClient(client);
        document.setTitle(request.title());
        document.setContent(request.content());

        document = documentRepository.save(document);

        // Chunk and embed the document content
        createChunks(document);

        return toResponse(document);
    }

    private void createChunks(Document document) {
        List<String> textChunks = chunkText(document.getContent());
        List<Chunk> chunks = new ArrayList<>();

        for (int i = 0; i < textChunks.size(); i++) {
            String text = textChunks.get(i);
            float[] embedding = embeddingService.embed(text);

            Chunk chunk = new Chunk();
            chunk.setDocument(document);
            chunk.setChunkIndex(i);
            chunk.setText(text);
            chunk.setEmbedding(embedding);
            chunks.add(chunk);
        }

        document.getChunks().addAll(chunks);
        documentRepository.save(document);
        log.info("Created {} chunks for document '{}'", chunks.size(), document.getTitle());
    }

    /**
     * Split text into overlapping chunks of ~150 words.
     * Overlap: 25 words. Final chunk < 50 words is merged into previous.
     */
    public List<String> chunkText(String text) {
        String[] words = text.trim().split("\\s+");

        if (words.length <= 150) {
            return List.of(String.join(" ", words));
        }

        int targetSize = 150;
        int overlap = 25;
        int step = targetSize - overlap;

        List<int[]> ranges = new ArrayList<>();
        int start = 0;

        while (start < words.length) {
            int end = Math.min(start + targetSize, words.length);
            ranges.add(new int[]{start, end});
            if (end >= words.length) break;
            start += step;
        }

        // Merge final chunk if < 50 words
        if (ranges.size() > 1) {
            int[] last = ranges.getLast();
            if (last[1] - last[0] < 50) {
                ranges.removeLast();
                ranges.getLast()[1] = last[1];
            }
        }

        return ranges.stream()
                .map(r -> String.join(" ", Arrays.copyOfRange(words, r[0], r[1])))
                .toList();
    }

    public List<SearchResultItem> searchDocuments(String query) {
        Map<UUID, Double> semanticScores   = fetchSemanticScores(query);
        Map<UUID, Double> rawKeywordScores = fetchRawKeywordScores(query);
        List<Map.Entry<UUID, Double>> ranked = rankDocuments(semanticScores, rawKeywordScores);

        Map<UUID, Document> docs = documentRepository.findAllById(
                        ranked.stream().map(Map.Entry::getKey).toList())
                .stream()
                .collect(Collectors.toMap(Document::getId, d -> d));

        return ranked.stream()
                .<SearchResultItem>map(e -> {
                    Document doc = docs.get(e.getKey());
                    log.info("Document '{}' matched '{}' score={}", doc.getTitle(), query, e.getValue());
                    return SearchResultItem.fromDocument(toResponse(doc), e.getValue());
                })
                .toList();
    }

    private Map<UUID, Double> fetchSemanticScores(String query) {
        String queryVec = embeddingToString(embeddingService.embed(query));
        Map<UUID, Double> scores = new HashMap<>();
        for (Object[] row : chunkRepository.findTopDocumentsByEmbedding(queryVec)) {
            double score = ((Number) row[1]).doubleValue();
            if (Double.isFinite(score)) {
                scores.put((UUID) row[0], score);
            }
        }
        return scores;
    }

    private Map<UUID, Double> fetchRawKeywordScores(String query) {
        Map<UUID, Double> scores = new HashMap<>();
        for (Object[] row : documentRepository.findDocumentIdsByKeyword(query)) {
            scores.put((UUID) row[0], ((Number) row[1]).doubleValue());
        }
        return scores;
    }

    private List<Map.Entry<UUID, Double>> rankDocuments(Map<UUID, Double> semanticScores,
                                                        Map<UUID, Double> rawKeywordScores) {
        Map<UUID, Double> keywordScores = normaliseKeywordScores(rawKeywordScores);

        Set<UUID> allDocIds = new HashSet<>(semanticScores.keySet());
        allDocIds.addAll(keywordScores.keySet());

        return allDocIds.stream()
                .map(docId -> {
                    double semantic = semanticScores.getOrDefault(docId, 0.0);
                    double keyword  = keywordScores.getOrDefault(docId, 0.0);
                    double score    = KEYWORD_WEIGHT * keyword + (1 - KEYWORD_WEIGHT) * semantic;
                    // tsvector keyword matches are always relevant (no false positives), so floor
                    // their score at the threshold rather than letting the semantic deficit drag
                    // them below it. A combined semantic + keyword result will naturally score
                    // above this floor and rank higher, preserving the intended hierarchy.
                    if (rawKeywordScores.containsKey(docId) && score < SIMILARITY_THRESHOLD) {
                        score = SIMILARITY_THRESHOLD;
                    }
                    return Map.entry(docId, score);
                })
                .filter(e -> e.getValue() >= SIMILARITY_THRESHOLD)
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .toList();
    }

    /**
     * Normalises raw ts_rank keyword scores to [0, 1] by dividing by the largest score
     * in the result set, clamped to a minimum floor of MIN_KEYWORD_NORMALISER.
     * Without the floor, a result set where all ts_rank values are very small (e.g. 0.003)
     * would be divided by 0.003, inflating every score to near 1.0 and misrepresenting
     * weak keyword matches as strong ones. Clamping the denominator to at least
     * MIN_KEYWORD_NORMALISER keeps proportionally weak matches proportionally small.
     */
    private Map<UUID, Double> normaliseKeywordScores(Map<UUID, Double> raw) {
        if (raw.isEmpty()) return Map.of();
        double max        = Collections.max(raw.values());
        double normaliser = Math.max(max, MIN_KEYWORD_NORMALISER);
        Map<UUID, Double> result = new HashMap<>();
        raw.forEach((id, score) -> result.put(id, score / normaliser));
        return result;
    }

    private static String embeddingToString(float[] v) {
        StringJoiner sj = new StringJoiner(",", "[", "]");
        for (float f : v) sj.add(String.valueOf(f));
        return sj.toString();
    }

    public DocumentResponse toResponse(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getClient().getId(),
                document.getTitle(),
                document.getContent(),
                document.getCreatedAt()
        );
    }
}
