package com.baz.searchapi.integration;

import com.baz.searchapi.config.TestcontainersConfig;
import com.baz.searchapi.service.EmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class DocumentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE clients CASCADE");
        when(embeddingService.embed(anyString())).thenReturn(new float[384]);
    }

    // --- Document creation ---

    @Test
    void createDocument_happyPath_returns201WithAllFields() throws Exception {
        UUID clientId = createClient("jane@example.com");

        mockMvc.perform(post("/clients/" + clientId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Utility Bill",
                                  "content": "This is a utility bill."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.client_id").value(clientId.toString()))
                .andExpect(jsonPath("$.title").value("Utility Bill"))
                .andExpect(jsonPath("$.content").value("This is a utility bill."))
                .andExpect(jsonPath("$.created_at").exists());
    }

    @Test
    void createDocument_clientNotFound_returns404() throws Exception {
        mockMvc.perform(post("/clients/" + UUID.randomUUID() + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Doc","content":"Content"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Client not found"));
    }

    @Test
    void createDocument_invalidClientUuid_returns400() throws Exception {
        mockMvc.perform(post("/clients/not-a-uuid/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Doc","content":"Content"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid value for parameter 'id'"));
    }

    @Test
    void createDocument_duplicateTitleSameClient_returns409() throws Exception {
        UUID clientId = createClient("jane@example.com");

        String body = """
                {"title":"Passport Copy","content":"Identity document."}
                """;

        mockMvc.perform(post("/clients/" + clientId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/clients/" + clientId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "A document with this title already exists for this client"));
    }

    @Test
    void createDocument_sameTitleDifferentClient_returns201() throws Exception {
        UUID clientA = createClient("alice@example.com");
        UUID clientB = createClient("bob@example.com");

        String body = """
                {"title":"Tax Return","content":"Annual tax return."}
                """;

        mockMvc.perform(post("/clients/" + clientA + "/documents")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/clients/" + clientB + "/documents")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void createDocument_missingTitle_returns400() throws Exception {
        UUID clientId = createClient("jane@example.com");

        mockMvc.perform(post("/clients/" + clientId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"Some content"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    void createDocument_missingContent_returns400() throws Exception {
        UUID clientId = createClient("jane@example.com");

        mockMvc.perform(post("/clients/" + clientId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"My Doc"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.content").exists());
    }

    @Test
    void createDocument_shortContent_createsExactlyOneChunk() throws Exception {
        UUID clientId = createClient("jane@example.com");
        UUID docId = createDocumentAndGetId(clientId, "Short Doc", "This is a short document.");

        Integer chunkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chunks WHERE document_id = ?",
                Integer.class, docId);

        assertEquals(1, chunkCount);
    }

    @Test
    void createDocument_longContent_createsMultipleChunks() throws Exception {
        UUID clientId = createClient("jane@example.com");

        // Build content with > 150 words to force chunking
        String content = "word ".repeat(200).trim();
        UUID docId = createDocumentAndGetId(clientId, "Long Doc", content);

        Integer chunkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chunks WHERE document_id = ?",
                Integer.class, docId);

        assertNotNull(chunkCount);
        assertTrue(chunkCount > 1, "Long content should produce multiple chunks, got " + chunkCount);
    }

    @Test
    void createDocument_1000WordContent_createsExactlyEightChunks() throws Exception {
        // step=125, targetSize=150 → ranges [0,150],[125,275],...,[875,1000]
        // last chunk = 125 words (>= 50 min) → kept, no merge → 8 chunks total
        UUID clientId = createClient("chunks@example.com");
        String content = "word ".repeat(1000).trim();
        UUID docId = createDocumentAndGetId(clientId, "Chunk Count Test", content);

        Integer chunkCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chunks WHERE document_id = ?",
                Integer.class, docId);

        assertEquals(8, chunkCount);
    }

    @Test
    void createDocument_embeddingRoundTrip_vectorStoredAndRetrievable() throws Exception {
        // Use a distinctive non-zero embedding to verify VectorConverter stores it faithfully
        float[] distinctiveEmbedding = new float[384];
        distinctiveEmbedding[0] = 0.5f;
        distinctiveEmbedding[100] = -0.3f;
        distinctiveEmbedding[383] = 0.9f;

        when(embeddingService.embed(anyString())).thenReturn(distinctiveEmbedding);

        UUID clientId = createClient("embed@example.com");
        UUID docId = createDocumentAndGetId(clientId, "Embed Test", "Embedding round-trip test.");

        // Read the stored vector directly from DB (as text in pgvector format)
        String storedVec = jdbcTemplate.queryForObject(
                "SELECT embedding::text FROM chunks WHERE document_id = ? LIMIT 1",
                String.class, docId);

        assertNotNull(storedVec);
        assertTrue(storedVec.startsWith("["), "pgvector format should start with '['");
        assertTrue(storedVec.endsWith("]"), "pgvector format should end with ']'");

        // Parse back and verify key positions round-tripped correctly (within float precision)
        String[] parts = storedVec.substring(1, storedVec.length() - 1).split(",");
        assertEquals(384, parts.length);
        assertEquals(0.5f, Float.parseFloat(parts[0].trim()), 0.0001f);
        assertEquals(-0.3f, Float.parseFloat(parts[100].trim()), 0.0001f);
        assertEquals(0.9f, Float.parseFloat(parts[383].trim()), 0.0001f);
    }

    @Test
    void createDocument_titleDuplicateCheckIsCaseInsensitive() throws Exception {
        UUID clientId = createClient("jane@example.com");

        mockMvc.perform(post("/clients/" + clientId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Passport Copy","content":"Content"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/clients/" + clientId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"passport copy","content":"Content"}
                                """))
                .andExpect(status().isConflict());
    }

    // --- Helpers ---

    private UUID createClient(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "first_name": "Test",
                                  "last_name": "User",
                                  "email": "%s"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        // Extract UUID from JSON "id" field
        String id = body.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        return UUID.fromString(id);
    }

    private UUID createDocumentAndGetId(UUID clientId, String title, String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/clients/" + clientId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","content":"%s"}
                                """.formatted(title, content)))
                .andExpect(status().isCreated())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String id = body.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        return UUID.fromString(id);
    }
}
