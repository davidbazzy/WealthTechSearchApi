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

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ClientIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmbeddingService embeddingService;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE clients CASCADE");
        when(embeddingService.embed(anyString())).thenReturn(new float[384]);
    }

    // --- Client creation ---

    @Test
    void createClient_happyPath_returns201WithAllFields() throws Exception {
        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "first_name": "Jane",
                                  "last_name": "Smith",
                                  "email": "jane@example.com",
                                  "description": "Senior advisor"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.first_name").value("Jane"))
                .andExpect(jsonPath("$.last_name").value("Smith"))
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.description").value("Senior advisor"));
    }

    @Test
    void createClient_withSocialLinks_linksPersistedAndReturned() throws Exception {
        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "first_name": "Bob",
                                  "last_name": "Jones",
                                  "email": "bob@example.com",
                                  "social_links": ["https://linkedin.com/bob", "https://twitter.com/bob"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.social_links", hasSize(2)))
                .andExpect(jsonPath("$.social_links", containsInAnyOrder(
                        "https://linkedin.com/bob", "https://twitter.com/bob")));
    }

    @Test
    void createClient_minimalFields_noDescriptionOrLinks_returns201() throws Exception {
        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "first_name": "Alice",
                                  "last_name": "Brown",
                                  "email": "alice@example.com"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void createClient_duplicateEmail_returns409() throws Exception {
        String body = """
                {
                  "first_name": "Alice",
                  "last_name": "Brown",
                  "email": "dupe@example.com"
                }
                """;

        mockMvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/clients").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("A client with this email already exists"));
    }

    @Test
    void createClient_duplicateEmail_caseInsensitive_returns409() throws Exception {
        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"first_name":"John","last_name":"Doe","email":"JOHN@EXAMPLE.COM"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"first_name":"John","last_name":"Doe","email":"john@example.com"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void createClient_missingFirstName_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"last_name":"Doe","email":"john@example.com"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.first_name").exists());
    }

    @Test
    void createClient_missingLastName_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"first_name":"John","email":"john@example.com"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.last_name").exists());
    }

    @Test
    void createClient_missingEmail_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"first_name":"John","last_name":"Doe"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void createClient_invalidEmailFormat_returns400() throws Exception {
        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"first_name":"John","last_name":"Doe","email":"not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void createClient_blankFirstName_returns400() throws Exception {
        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"first_name":"   ","last_name":"Doe","email":"john@example.com"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.first_name").exists());
    }

    // --- Client search (tsvector full-text) ---

    @Test
    void searchClients_byFirstName_returnsMatch() throws Exception {
        createJane();

        mockMvc.perform(get("/search").param("q", "Jane"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("client"))
                .andExpect(jsonPath("$[0].first_name").value("Jane"));
    }

    @Test
    void searchClients_byLastName_returnsMatch() throws Exception {
        createJane();

        mockMvc.perform(get("/search").param("q", "Smith"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].last_name").value("Smith"));
    }

    @Test
    void searchClients_byDescription_returnsMatch() throws Exception {
        createJane();

        mockMvc.perform(get("/search").param("q", "retirement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].first_name").value("Jane"));
    }

    @Test
    void searchClients_partialWordInDescription_returnsMatch() throws Exception {
        createJane();

        // "retire" is a substring of "retirement" in Jane's description
        mockMvc.perform(get("/search").param("q", "retire"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].first_name").value("Jane"));
    }

    @Test
    void searchClients_multipleClients_allMatchingReturned() throws Exception {
        createJane();
        createBob();

        // "advisor" appears in both descriptions
        mockMvc.perform(get("/search").param("q", "advisor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void searchClients_noMatch_returnsEmpty() throws Exception {
        createJane();

        mockMvc.perform(get("/search").param("q", "xyznonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void searchClients_caseInsensitive_returnsMatch() throws Exception {
        createJane();

        mockMvc.perform(get("/search").param("q", "JANE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].first_name").value("Jane"));
    }

    @Test
    void searchClients_byEmailDomain_returnsMatch() throws Exception {
        // Spec's canonical example: "outlook" should match "john.doe@outlook.com".
        // tsvector treats the whole email as one opaque token, so ILIKE on the email column
        // is required for partial domain/local-part matching.
        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "first_name": "John",
                                  "last_name": "Doe",
                                  "email": "john.doe@outlook.com"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/search").param("q", "outlook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("client"))
                .andExpect(jsonPath("$[0].email").value("john.doe@outlook.com"));
    }

    @Test
    void searchClients_byEmailLocalPart_returnsMatch() throws Exception {
        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "first_name": "Alice",
                                  "last_name": "Brown",
                                  "email": "alice.brown@example.com"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/search").param("q", "alice.brown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value("alice.brown@example.com"));
    }

    // --- Helpers ---

    private void createJane() throws Exception {
        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "first_name": "Jane",
                                  "last_name": "Smith",
                                  "email": "jane@example.com",
                                  "description": "Retirement advisor specializing in pension planning"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    private void createBob() throws Exception {
        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "first_name": "Bob",
                                  "last_name": "Johnson",
                                  "email": "bob@example.com",
                                  "description": "Investment advisor and portfolio manager"
                                }
                                """))
                .andExpect(status().isCreated());
    }
}
