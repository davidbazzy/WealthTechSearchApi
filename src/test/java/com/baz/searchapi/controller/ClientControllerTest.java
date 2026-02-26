package com.baz.searchapi.controller;

import tools.jackson.databind.json.JsonMapper;
import com.baz.searchapi.model.dto.ClientRequest;
import com.baz.searchapi.service.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private EmbeddingService embeddingService;

    @Test
    void createClient_happyPath_returns201() throws Exception {
        var request = new ClientRequest("John", "Doe", "john@outlook.com",
                "High net worth client", List.of("https://linkedin.com/in/johndoe"));

        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.first_name").value("John"))
                .andExpect(jsonPath("$.last_name").value("Doe"))
                .andExpect(jsonPath("$.email").value("john@outlook.com"))
                .andExpect(jsonPath("$.description").value("High net worth client"))
                .andExpect(jsonPath("$.social_links[0]").value("https://linkedin.com/in/johndoe"));
    }

    @Test
    void createClient_missingFirstName_returns400() throws Exception {
        var request = new ClientRequest(null, "Doe", "john@outlook.com", null, null);

        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.first_name").exists());
    }

    @Test
    void createClient_blankEmail_returns400() throws Exception {
        var request = new ClientRequest("John", "Doe", "", null, null);

        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void createClient_duplicateEmail_returns409() throws Exception {
        var request = new ClientRequest("John", "Doe", "john@outlook.com", null, null);

        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second attempt with same email
        var request2 = new ClientRequest("Jane", "Smith", "john@outlook.com", null, null);

        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("A client with this email already exists"));
    }

    @Test
    void createClient_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed JSON request body"));
    }

    @Test
    void createClient_minimalFields_returns201() throws Exception {
        var request = new ClientRequest("Alice", "Wonder", "alice@example.com", null, null);

        mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.first_name").value("Alice"))
                .andExpect(jsonPath("$.description").doesNotExist());
    }
}
