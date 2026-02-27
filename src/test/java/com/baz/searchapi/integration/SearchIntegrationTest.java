package com.baz.searchapi.integration;

import com.baz.searchapi.config.TestMockMvcConfig;
import com.baz.searchapi.config.TestcontainersConfig;

import com.baz.searchapi.service.EmbeddingService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests for the hybrid search pipeline (pgvector KNN + tsvector keyword).
 *
 * <p>Strategy: EmbeddingService is mocked to return deterministic, orthogonal unit vectors
 * partitioned into 4 categories (96-dim blocks within 384 dimensions):
 * <ul>
 *   <li>Block 0 (dims 0–95):   ADDRESS category</li>
 *   <li>Block 1 (dims 96–191): IDENTITY category</li>
 *   <li>Block 2 (dims 192–287): PORTFOLIO category</li>
 *   <li>Block 3 (dims 288–383): TAX category</li>
 * </ul>
 * Cosine similarity between two vectors in the same block = 1.0; different blocks = 0.0.
 * An all-zero vector has cosine similarity = 0 with everything (≪ threshold 0.4).
 *
 * <p>Test data is inserted once in @BeforeAll. The Spring context (and the PostgreSQL container)
 * is reused across all tests in this class. The EmbeddingService mock is re-stubbed in @BeforeEach
 * because Spring Boot resets @MockitoBean stubs before each test method.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, TestMockMvcConfig.class})
class SearchIntegrationTest {

    // --- Category embeddings ---

    /** Dims 0-95 = 1.0; all others = 0.0 */
    private static final float[] ADDR_EMBED   = categoryEmbedding(0);
    /** Dims 96-191 = 1.0; all others = 0.0 */
    private static final float[] IDENT_EMBED  = categoryEmbedding(1);
    /** Dims 192-287 = 1.0; all others = 0.0 */
    private static final float[] PORT_EMBED   = categoryEmbedding(2);
    /** Dims 288-383 = 1.0; all others = 0.0 */
    private static final float[] TAX_EMBED    = categoryEmbedding(3);
    /** All zeros — cosine sim = 0 with everything (below the 0.4 threshold) */
    private static final float[] ZERO_EMBED   = new float[384];

    // --- Infrastructure ---

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private EmbeddingService embeddingService;

    // --- Test data IDs (set up once, reused across all tests) ---

    private UUID janeId;   // "Retirement advisor"
    private UUID bobId;    // "Portfolio manager"
    private UUID aliceId;  // "Tax consultant"


    // --- One-time data setup ---

    @BeforeAll
    void setUpTestData() throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE clients CASCADE");

        // Configure mock: return embedding based on what each document's content contains.
        // These stubs are used during document creation in @BeforeAll.
        stubEmbeddingMock();

        // Clients (unique descriptions drive tsvector matches)
        janeId  = createClient("Jane",  "Smith",   "jane@search.com",  "Retirement advisor specializing in pension planning");
        bobId   = createClient("Bob",   "Johnson", "bob@search.com",   "Portfolio manager and investment specialist");
        aliceId = createClient("Alice", "Williams","alice@search.com", "Tax consultant and financial planner");

        // Documents — content keywords drive which category embedding is returned by the mock
        createDocument(janeId,  "Utility Bill",
                "This document shows the residential address and utility usage for the property.");
        createDocument(janeId,  "Passport Copy",
                "This document contains passport details used for identity verification purposes.");
        createDocument(bobId,   "Investment Portfolio Statement",
                "Quarterly portfolio statement detailing investment performance and asset allocation.");
        createDocument(aliceId, "Tax Return 2023",
                "Annual income tax return covering earnings deductions and tax obligations.");
        createDocument(bobId,   "Risk Management Overview",
                "Overview of diversified allocation strategies to minimize financial risk exposure.");
    }

    /**
     * Re-stub the EmbeddingService before each test.
     * Spring Boot's MockitoTestExecutionListener resets @MockitoBean stubs before each
     * test method, so we must re-apply stubs in @BeforeEach.
     */
    @BeforeEach
    void stubBeforeEachTest() {
        stubEmbeddingMock();
    }

    // --- Client search (tsvector full-text) ---

    @Test
    void search_clientByFirstName_returnsClientResult() throws Exception {
        mockMvc.perform(get("/search").param("q", "Jane"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'client' && @.first_name == 'Jane')]").exists());
    }

    @Test
    void search_clientByDescription_retirementAdvisor() throws Exception {
        mockMvc.perform(get("/search").param("q", "retirement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'client' && @.first_name == 'Jane')]").exists());
    }

    @Test
    void search_clientEnglishStemming_retireMatchesRetirement() throws Exception {
        mockMvc.perform(get("/search").param("q", "retire"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'client' && @.first_name == 'Jane')]").exists());
    }

    @Test
    void search_clientByDescription_pensionMatchesPlanningDescription() throws Exception {
        mockMvc.perform(get("/search").param("q", "pension"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'client' && @.first_name == 'Jane')]").exists());
    }

    // --- Document semantic search (pgvector KNN) ---

    @Test
    void search_semanticAddress_returnsUtilityBill() throws Exception {
        // Query "address" → ADDR_EMBED → cosine sim 1.0 with Utility Bill chunks
        mockMvc.perform(get("/search").param("q", "address"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'document' && @.title == 'Utility Bill')]").exists());
    }

    @Test
    void search_semanticIdentity_returnsPassportCopy() throws Exception {
        mockMvc.perform(get("/search").param("q", "identity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'document' && @.title == 'Passport Copy')]").exists());
    }

    @Test
    void search_semanticPortfolio_returnsPortfolioStatement() throws Exception {
        mockMvc.perform(get("/search").param("q", "portfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'document' && @.title == 'Investment Portfolio Statement')]").exists());
    }

    @Test
    void search_semanticTax_returnsTaxReturn() throws Exception {
        mockMvc.perform(get("/search").param("q", "tax"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'document' && @.title == 'Tax Return 2023')]").exists());
    }

    @Test
    void search_semanticAddress_doesNotReturnIdentityDocument() throws Exception {
        // Cosine similarity between ADDR_EMBED and IDENT_EMBED = 0.0 < threshold 0.4
        mockMvc.perform(get("/search").param("q", "address"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Passport Copy')]").doesNotExist());
    }

    // --- Keyword search (tsvector document full-text) ---

    @Test
    void search_keywordOnly_statementMatchesPortfolioDocument() throws Exception {
        // Query "statement" → ZERO_EMBED → no semantic matches
        // BUT tsvector of "Investment Portfolio Statement" contains "statement"
        // → keyword path adds it at the base threshold (0.4)
        mockMvc.perform(get("/search").param("q", "statement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Investment Portfolio Statement')]").exists());
    }

    @Test
    void search_keywordOnly_earningsMatchesTaxReturn() throws Exception {
        // "earnings" is in the Tax Return content but NOT in the embedding stub →
        // returns ZERO_EMBED (no semantic match) but tsvector keyword path still finds it.
        mockMvc.perform(get("/search").param("q", "earnings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Tax Return 2023')]").exists());
    }

    // --- Hybrid results (client + document in same response) ---

    @Test
    void search_portfolioQuery_returnsBothClientAndDocument() throws Exception {
        // Bob (client) has "portfolio" in description → tsvector match
        // "Investment Portfolio Statement" (document) has PORTFOLIO embedding + tsvector match
        mockMvc.perform(get("/search").param("q", "portfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'client' && @.first_name == 'Bob')]").exists())
                .andExpect(jsonPath("$[?(@.type == 'document' && @.title == 'Investment Portfolio Statement')]").exists());
    }

    // --- Keyword-only document search ---

    @Test
    void search_keywordOnly_quarterlyMatchesPortfolioStatement() throws Exception {
        // "quarterly" → ZERO_EMBED (no semantic trigger); appears in Portfolio Statement content
        // Risk Management Overview does not contain "quarterly" → excluded
        mockMvc.perform(get("/search").param("q", "quarterly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Investment Portfolio Statement')]").exists())
                .andExpect(jsonPath("$[?(@.title == 'Risk Management Overview')]").doesNotExist());
    }

    @Test
    void search_keywordOnly_exposureMatchesRiskReport() throws Exception {
        // "exposure" → ZERO_EMBED; appears only in Risk Management Overview content
        mockMvc.perform(get("/search").param("q", "exposure"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Risk Management Overview')]").exists())
                .andExpect(jsonPath("$[?(@.title == 'Passport Copy')]").doesNotExist());
    }

    // --- Semantic-only document search ---

    @Test
    void search_semanticOnly_investmentMatchesRiskReportWithoutKeyword() throws Exception {
        // "investment" → PORT_EMBED → cosine sim 1.0 with Risk Management Overview (also PORT_EMBED)
        // "investment" does NOT appear in Risk Management Overview content → pure semantic match
        mockMvc.perform(get("/search").param("q", "investment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Risk Management Overview')]").exists());
    }

    @Test
    void search_semanticOnly_wrongCategoryNotReturned() throws Exception {
        // "investment" → PORT_EMBED; "Passport Copy" has IDENT_EMBED → cosine sim = 0.0 → excluded
        mockMvc.perform(get("/search").param("q", "investment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Passport Copy')]").doesNotExist());
    }

    // --- Hybrid (semantic + keyword) document search ---

    @Test
    void search_hybrid_allocationMatchesRiskReportViaBothPaths() throws Exception {
        // "allocation" → PORT_EMBED (semantic) AND "allocation" in Risk Management Overview content (keyword)
        // Both paths contribute → combined score higher than semantic-only score of 0.7
        mockMvc.perform(get("/search").param("q", "allocation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Risk Management Overview')]").exists());
    }

    @Test
    void search_hybrid_ranksAboveSemanticOnly() throws Exception {
        // "portfolio" → PORT_EMBED
        // "Investment Portfolio Statement": PORT_EMBED + "portfolio" in title → hybrid (higher score)
        // "Risk Management Overview": PORT_EMBED only, "portfolio" absent from content → semantic-only (lower score)
        MvcResult result = mockMvc.perform(get("/search").param("q", "portfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == 'Investment Portfolio Statement')]").exists())
                .andExpect(jsonPath("$[?(@.title == 'Risk Management Overview')]").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.indexOf("Investment Portfolio Statement") < body.indexOf("Risk Management Overview"),
                "Hybrid match should rank above semantic-only match");
    }

    // --- No matches on one side ---

    @Test
    void search_noDocumentMatches_onlyClientReturned() throws Exception {
        // "retirement" → ZERO_EMBED + not in any document content → no document results
        // Jane has "retirement" in description → client result only
        mockMvc.perform(get("/search").param("q", "retirement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'client')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.type == 'document')]").isEmpty());
    }

    @Test
    void search_noClientMatches_onlyDocumentReturned() throws Exception {
        // "address" → ADDR_EMBED + in Utility Bill content → document result
        // No client has "address" in any field → no client results
        mockMvc.perform(get("/search").param("q", "address"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'document')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.type == 'client')]").isEmpty());
    }

    // --- Response shape ---

    @Test
    void search_clientResult_hasCorrectFields() throws Exception {
        mockMvc.perform(get("/search").param("q", "retirement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'client')].type").value(hasItem("client")))
                .andExpect(jsonPath("$[?(@.type == 'client')].first_name").value(hasItem("Jane")))
                .andExpect(jsonPath("$[?(@.type == 'client')].last_name").value(hasItem("Smith")))
                .andExpect(jsonPath("$[?(@.type == 'client')].email").value(hasItem("jane@search.com")));
    }

    @Test
    void search_clientResult_hasNoDocumentFields() throws Exception {
        mockMvc.perform(get("/search").param("q", "retirement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'client')].title").isEmpty())
                .andExpect(jsonPath("$[?(@.type == 'client')].content").isEmpty())
                .andExpect(jsonPath("$[?(@.type == 'client')].client_id").isEmpty());
    }

    @Test
    void search_documentResult_hasCorrectFields() throws Exception {
        mockMvc.perform(get("/search").param("q", "address"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'document')].title").value(hasItem("Utility Bill")))
                .andExpect(jsonPath("$[?(@.type == 'document')].content").value(hasItem(containsString("address"))))
                .andExpect(jsonPath("$[?(@.type == 'document')].client_id").value(hasItem(janeId.toString())))
                .andExpect(jsonPath("$[?(@.type == 'document')].created_at").isNotEmpty());
    }

    @Test
    void search_documentResult_hasNoClientFields() throws Exception {
        mockMvc.perform(get("/search").param("q", "address"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'document')].first_name").isEmpty())
                .andExpect(jsonPath("$[?(@.type == 'document')].last_name").isEmpty())
                .andExpect(jsonPath("$[?(@.type == 'document')].email").isEmpty());
    }

    @Test
    void search_noRelevanceScoreInResponse() throws Exception {
        mockMvc.perform(get("/search").param("q", "address"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].relevance_score").doesNotExist());
    }

    // --- Edge cases ---

    @Test
    void search_noMatchingDocumentsOrClients_returnsEmptyArray() throws Exception {
        // ZERO_EMBED has cosine sim = 0 with all documents; no tsvector match
        mockMvc.perform(get("/search").param("q", "xyznonexistentterm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
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

    @Test
    void search_queryIsTrimmedBeforeProcessing() throws Exception {
        // Padded with spaces — service should trim and still find Jane
        mockMvc.perform(get("/search").param("q", "  retirement  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.first_name == 'Jane')]").exists());
    }

    @Test
    void search_responseIsArray() throws Exception {
        mockMvc.perform(get("/search").param("q", "anything"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // --- Helpers ---

    /**
     * Builds a 384-dim vector with 1.0 in the 96-element block for the given category
     * and 0.0 everywhere else.
     * Cosine similarity between two same-category vectors = 1.0.
     * Cosine similarity between two different-category vectors = 0.0.
     */
    private static float[] categoryEmbedding(int category) {
        float[] v = new float[384];
        int blockStart = category * 96;
        for (int i = blockStart; i < blockStart + 96; i++) {
            v[i] = 1.0f;
        }
        return v;
    }

    /**
     * Configures the EmbeddingService mock to return category embeddings based on
     * keywords in the text being embedded. Unknown text returns ZERO_EMBED (no matches).
     */
    private void stubEmbeddingMock() {
        when(embeddingService.embed(anyString())).thenAnswer(inv -> {
            String text = ((String) inv.getArgument(0)).toLowerCase();
            if (text.contains("address") || text.contains("utility") || text.contains("residential")) {
                return ADDR_EMBED;
            }
            if (text.contains("identity") || text.contains("passport") || text.contains("verification")) {
                return IDENT_EMBED;
            }
            if (text.contains("portfolio") || text.contains("investment") || text.contains("allocation")) {
                return PORT_EMBED;
            }
            if (text.contains("tax") || text.contains("income") || text.contains("deductions")) {
                return TAX_EMBED;
            }
            return ZERO_EMBED;
        });
    }

    private UUID createClient(String firstName, String lastName, String email, String description)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "first_name": "%s",
                                  "last_name": "%s",
                                  "email": "%s",
                                  "description": "%s"
                                }
                                """.formatted(firstName, lastName, email, description)))
                .andExpect(status().isCreated())
                .andReturn();

        return extractId(result);
    }

    private UUID createDocument(UUID clientId, String title, String content) throws Exception {
        MvcResult result = mockMvc.perform(post("/clients/" + clientId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","content":"%s"}
                                """.formatted(title, content)))
                .andExpect(status().isCreated())
                .andReturn();

        return extractId(result);
    }

    private static UUID extractId(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        String id = body.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        return UUID.fromString(id);
    }
}
