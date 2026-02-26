package com.baz.searchapi.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CosineSimilarityTest {

    @Test
    void identicalVectors_returnsOne() {
        float[] a = {1.0f, 2.0f, 3.0f};
        double similarity = EmbeddingService.cosineSimilarity(a, a);
        assertEquals(1.0, similarity, 1e-6);
    }

    @Test
    void oppositeVectors_returnsNegativeOne() {
        float[] a = {1.0f, 0.0f, 0.0f};
        float[] b = {-1.0f, 0.0f, 0.0f};
        double similarity = EmbeddingService.cosineSimilarity(a, b);
        assertEquals(-1.0, similarity, 1e-6);
    }

    @Test
    void orthogonalVectors_returnsZero() {
        float[] a = {1.0f, 0.0f};
        float[] b = {0.0f, 1.0f};
        double similarity = EmbeddingService.cosineSimilarity(a, b);
        assertEquals(0.0, similarity, 1e-6);
    }

    @Test
    void similarVectors_returnsHighScore() {
        float[] a = {1.0f, 2.0f, 3.0f};
        float[] b = {1.1f, 2.1f, 3.1f};
        double similarity = EmbeddingService.cosineSimilarity(a, b);
        assertTrue(similarity > 0.99, "Very similar vectors should have high cosine similarity");
    }

    @Test
    void zeroVector_returnsZero() {
        float[] a = {0.0f, 0.0f, 0.0f};
        float[] b = {1.0f, 2.0f, 3.0f};
        double similarity = EmbeddingService.cosineSimilarity(a, b);
        assertEquals(0.0, similarity, 1e-6);
    }

    @Test
    void byteSerialization_roundTrips() {
        float[] original = {0.1f, -0.5f, 0.99f, 0.0f};
        byte[] bytes = EmbeddingService.toBytes(original);
        float[] restored = EmbeddingService.toFloats(bytes);
        assertArrayEquals(original, restored, 1e-6f);
    }
}
