package com.baz.searchapi.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ChunkingTest {

    private final DocumentService documentService = new DocumentService(null, null, null, null);

    @Test
    void shortDocument_singleChunk() {
        String text = "This is a short document with just a few words.";
        List<String> chunks = documentService.chunkText(text);
        assertEquals(1, chunks.size());
        assertEquals(text, chunks.getFirst());
    }

    @Test
    void documentExactly150Words_singleChunk() {
        String text = generateWords(150);
        List<String> chunks = documentService.chunkText(text);
        assertEquals(1, chunks.size());
    }

    @Test
    void documentOver150Words_multipleChunks() {
        String text = generateWords(300);
        List<String> chunks = documentService.chunkText(text);
        assertTrue(chunks.size() > 1, "Should have multiple chunks");
        // Verify all original words appear in at least one chunk
        String[] originalWords = text.split("\\s+");
        for (String word : originalWords) {
            assertTrue(chunks.stream().anyMatch(c -> c.contains(word)),
                    "Word '" + word + "' should appear in at least one chunk");
        }
    }

    @Test
    void finalSmallChunk_mergedIntoPrevious() {
        // 170 words: chunk1=[0,149] (150 words), chunk2=[125,169] (45 words < 50) → merge
        String text = generateWords(170);
        List<String> chunks = documentService.chunkText(text);
        assertEquals(1, chunks.size(), "Small remainder (< 50 words) should be merged");
        assertEquals(170, chunks.getFirst().split("\\s+").length);
    }

    @Test
    void finalChunkAboveThreshold_notMerged() {
        // 180 words: chunk1=[0,149], chunk2=[125,179] (55 words >= 50) → NOT merged
        String text = generateWords(180);
        List<String> chunks = documentService.chunkText(text);
        assertEquals(2, chunks.size(), "Chunk with >= 50 words should NOT be merged");
    }

    @Test
    void largeDocument_correctOverlap() {
        // 500 words: step=125, chunks at [0,149], [125,274], [250,399], [375,499]
        String text = generateWords(500);
        List<String> chunks = documentService.chunkText(text);
        assertTrue(chunks.size() >= 3, "500-word doc should have at least 3 chunks");

        // Verify overlap: last 25 words of chunk 1 == first 25 words of chunk 2
        String[] chunk1Words = chunks.get(0).split("\\s+");
        String[] chunk2Words = chunks.get(1).split("\\s+");
        for (int i = 0; i < 25; i++) {
            assertEquals(chunk1Words[125 + i], chunk2Words[i],
                    "Overlap word " + i + " should match");
        }
    }

    @Test
    void emptyOrWhitespace_singleChunk() {
        List<String> chunks = documentService.chunkText("   ");
        assertEquals(1, chunks.size());
    }

    private String generateWords(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> "word" + i)
                .collect(Collectors.joining(" "));
    }
}
