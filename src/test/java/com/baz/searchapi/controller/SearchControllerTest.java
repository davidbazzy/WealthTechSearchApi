package com.baz.searchapi.controller;

import tools.jackson.databind.json.JsonMapper;
import com.baz.searchapi.model.dto.ClientRequest;
import com.baz.searchapi.model.dto.DocumentRequest;
import com.baz.searchapi.service.EmbeddingService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Random;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the search endpoint.
 * Uses a mocked EmbeddingService to avoid external dependencies.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchControllerTest {

    private static final int EMBEDDING_DIM = 384;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private EmbeddingService embeddingService;

    @BeforeEach
    void setUpMock() {
        // Reconfigure mock before each test because MockitoBean resets after each test method
        mockCategoryEmbeddingsForSemanticSearch();
    }

    @BeforeAll
    void setUp() throws Exception {
        final String johnClientId;
        final String janeClientId;
        // Configure mock to return category-based embeddings for semantic search
        mockCategoryEmbeddingsForSemanticSearch();

        // Client 1: John Doe — senior advisor
        johnClientId = createClient(new ClientRequest("John", "Doe", "john.doe@outlook.com",
                "Senior financial advisor specializing in wealth management",
                List.of("https://linkedin.com/in/johndoe")));

        // Client 2: Jane Smith — junior advisor
        janeClientId = createClient(new ClientRequest("Jane", "Smith", "jane.smith@example.com",
                "Junior advisor focused on retirement planning", null));

        // Client 3: Alice Johnson — no description
        createClient(new ClientRequest("Alice", "Johnson", "alice@bigbank.com", null, null));

        // Document 1 (Jane): utility bill — proof of address
        createDocument(janeClientId, new DocumentRequest("Utility Bill",
                "This document contains a utility bill from the electric company. "
                        + "It serves as proof of residential address for the client. "
                        + "The bill is dated January 2024 and shows the current home address."));

        // Document 2 (Jane): investment portfolio
        createDocument(janeClientId, new DocumentRequest("Investment Portfolio Summary",
                "Annual portfolio review for the client. The portfolio includes stocks, bonds, "
                        + "and mutual funds. Total asset value is approximately $2.5 million. "
                        + "The recommended strategy is a balanced growth approach with 60% equities "
                        + "and 40% fixed income. Risk tolerance is moderate."));

        // Document 3 (John): passport copy
        createDocument(johnClientId, new DocumentRequest("Passport Copy",
                "Scanned copy of the client's passport for identity verification purposes. "
                        + "The passport was issued by the United States government and is valid "
                        + "until December 2028. This document is used for KYC compliance."));

        // Document 4 (John): tax return
        createDocument(johnClientId, new DocumentRequest("Tax Return 2023",
                "Federal income tax return for the fiscal year 2023. "
                        + "Total reported income was $450,000. Includes wages, capital gains, "
                        + "and dividend income. Effective tax rate was 28%."));
    }

    // --- Response structure ---

    @Test
    void search_responseIsArray() throws Exception {
        mockMvc.perform(get("/search").param("q", "anything"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void search_eachItemHasType() throws Exception {
        mockMvc.perform(get("/search").param("q", "John"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").exists());
    }

    // --- Client substring search ---

    @Test
    void search_clientByEmail_findsMatch() throws Exception {
        mockMvc.perform(get("/search").param("q", "outlook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == 'john.doe@outlook.com')]").exists());
    }

    @Test
    void search_clientByFirstName_findsMatch() throws Exception {
        mockMvc.perform(get("/search").param("q", "John"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.first_name == 'John')]").exists());
    }

    @Test
    void search_clientByLastName_findsMatch() throws Exception {
        mockMvc.perform(get("/search").param("q", "Smith"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.last_name == 'Smith')]").exists());
    }

    @Test
    void search_clientByDescription_findsMatch() throws Exception {
        mockMvc.perform(get("/search").param("q", "retirement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'client' && @.first_name == 'Jane')]").exists());
    }

    @Test
    void search_clientCaseInsensitive_findsMatch() throws Exception {
        mockMvc.perform(get("/search").param("q", "OUTLOOK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == 'john.doe@outlook.com')]").exists());
    }

    @Test
    void search_partialEmail_findsMatch() throws Exception {
        mockMvc.perform(get("/search").param("q", "bigbank"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'client' && @.first_name == 'Alice')]").exists());
    }

    @Test
    void search_matchesMultipleClients() throws Exception {
        // "advisor" appears in both John's and Jane's descriptions
        mockMvc.perform(get("/search").param("q", "advisor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'client')]", hasSize(2)));
    }

    // --- Semantic document search ---

    @Test
    void search_semanticMatch_addressProofFindsUtilityBill() throws Exception {
        mockMvc.perform(get("/search").param("q", "address proof"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Utility Bill')]").exists());
    }

    @Test
    void search_semanticMatch_identityVerificationFindsPassport() throws Exception {
        mockMvc.perform(get("/search").param("q", "identity verification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Passport Copy')]").exists());
    }

    @Test
    void search_semanticMatch_financialAssetsFindsPortfolio() throws Exception {
        mockMvc.perform(get("/search").param("q", "financial assets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Investment Portfolio Summary')]").exists());
    }

    @Test
    void search_semanticMatch_incomeReportFindsTaxReturn() throws Exception {
        mockMvc.perform(get("/search").param("q", "income report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Tax Return 2023')]").exists());
    }

    @Test
    void search_semanticMatch_kycComplianceFindsPassport() throws Exception {
        mockMvc.perform(get("/search").param("q", "KYC compliance documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Passport Copy')]").exists());
    }

    @Test
    void search_documentResult_includesAllFields() throws Exception {
        mockMvc.perform(get("/search").param("q", "utility bill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'document')]").exists())
                .andExpect(jsonPath("$[?(@.title == 'Utility Bill')].id").exists())
                .andExpect(jsonPath("$[?(@.title == 'Utility Bill')].client_id").exists())
                .andExpect(jsonPath("$[?(@.title == 'Utility Bill')].title").exists())
                .andExpect(jsonPath("$[?(@.title == 'Utility Bill')].content").exists())
                .andExpect(jsonPath("$[?(@.title == 'Utility Bill')].created_at").exists());
    }

    @Test
    void search_documentResult_noRelevanceScoreInResponse() throws Exception {
        mockMvc.perform(get("/search").param("q", "utility bill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Utility Bill')].relevance_score").doesNotExist());
    }

    // --- Mixed results (clients + documents) ---

    @Test
    void search_returnsClientType() throws Exception {
        mockMvc.perform(get("/search").param("q", "Jane"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'client')]").exists());
    }

    @Test
    void search_returnsDocumentType() throws Exception {
        mockMvc.perform(get("/search").param("q", "utility bill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'document')]").exists());
    }

    // --- Edge cases ---

    @Test
    void search_noResults_returnsEmptyArray() throws Exception {
        mockMvc.perform(get("/search").param("q", "xyznonexistent123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void search_singleCharacter_isValid() throws Exception {
        mockMvc.perform(get("/search").param("q", "J"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'client')]").exists());
    }

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

    // --- Helpers ---

    private void mockCategoryEmbeddingsForSemanticSearch() {
        when(embeddingService.embed(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            return generateCategoryEmbedding(text);
        });
    }

    private String createClient(ClientRequest request) throws Exception {
        return objectMapper.readTree(
                mockMvc.perform(post("/clients")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()
        ).get("id").asString();
    }

    private void createDocument(String clientId, DocumentRequest request) throws Exception {
        mockMvc.perform(post("/clients/" + clientId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    /**
     * Generates a deterministic embedding based on semantic categories.
     * Texts containing similar keywords will produce similar embeddings.
     */
    private float[] generateCategoryEmbedding(String text) {
        String lower = text.toLowerCase();
        float[] embedding = new float[EMBEDDING_DIM];

        // Category weights - each category gets a distinct region of the embedding space
        float addressWeight = containsAny(lower, "utility", "bill", "address", "proof", "residential", "home", "electric") ? 1.0f : 0.0f;
        float identityWeight = containsAny(lower, "passport", "identity", "verification", "kyc", "compliance", "government") ? 1.0f : 0.0f;
        float portfolioWeight = containsAny(lower, "portfolio", "investment", "assets", "stocks", "bonds", "financial", "equities", "mutual") ? 1.0f : 0.0f;
        float taxWeight = containsAny(lower, "tax", "return", "income", "fiscal", "wages", "capital gains", "dividend") ? 1.0f : 0.0f;

        // Fill different regions of the embedding with category signals
        for (int i = 0; i < 96; i++) {
            embedding[i] = addressWeight;
        }
        for (int i = 96; i < 192; i++) {
            embedding[i] = identityWeight;
        }
        for (int i = 192; i < 288; i++) {
            embedding[i] = portfolioWeight;
        }
        for (int i = 288; i < 384; i++) {
            embedding[i] = taxWeight;
        }

        // Add some deterministic centered noise based on text hash to differentiate similar texts.
        // Noise is centered around 0 (range [-0.1, 0.1]) to prevent a positive bias that would
        // cause zero-category queries to have artificially high cosine similarity with all docs.
        Random random = new Random(text.hashCode());
        for (int i = 0; i < EMBEDDING_DIM; i++) {
            embedding[i] += (random.nextFloat() - 0.5f) * 0.2f;
        }

        // Normalize the embedding
        return normalize(embedding);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private float[] normalize(float[] v) {
        float norm = 0;
        for (float f : v) norm += f * f;
        norm = (float) Math.sqrt(norm);
        if (norm == 0) return v;

        float[] result = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            result[i] = v[i] / norm;
        }
        return result;
    }
}
