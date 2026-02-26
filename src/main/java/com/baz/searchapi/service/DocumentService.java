package com.baz.searchapi.service;

import com.baz.searchapi.model.dto.DocumentRequest;
import com.baz.searchapi.model.dto.DocumentResponse;
import com.baz.searchapi.model.entity.Chunk;
import com.baz.searchapi.model.entity.Client;
import com.baz.searchapi.model.entity.Document;
import com.baz.searchapi.repository.ClientRepository;
import com.baz.searchapi.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final ClientRepository clientRepository;
    private final EmbeddingService embeddingService;

    public DocumentService(DocumentRepository documentRepository, ClientRepository clientRepository, EmbeddingService embeddingService) {
        this.documentRepository = documentRepository;
        this.clientRepository = clientRepository;
        this.embeddingService = embeddingService;
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
            chunk.setEmbedding(EmbeddingService.toBytes(embedding));
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
