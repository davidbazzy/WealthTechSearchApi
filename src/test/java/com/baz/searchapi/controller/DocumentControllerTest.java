package com.baz.searchapi.controller;

import tools.jackson.databind.json.JsonMapper;
import com.baz.searchapi.model.dto.ClientRequest;
import com.baz.searchapi.model.dto.DocumentRequest;
import com.baz.searchapi.service.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private EmbeddingService embeddingService;

    private String createClientAndGetId() throws Exception {
        when(embeddingService.embed(anyString())).thenReturn(new float[384]);

        var request = new ClientRequest("John", "Doe", "john@outlook.com", null, null);
        MvcResult result = mockMvc.perform(post("/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString();
    }

    @Test
    void createDocument_happyPath_returns201() throws Exception {
        String clientId = createClientAndGetId();
        var docRequest = new DocumentRequest("Utility Bill", "This is a utility bill for address verification.");

        mockMvc.perform(post("/clients/" + clientId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(docRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.client_id").value(clientId))
                .andExpect(jsonPath("$.title").value("Utility Bill"))
                .andExpect(jsonPath("$.content").value("This is a utility bill for address verification."))
                .andExpect(jsonPath("$.created_at").exists());
    }

    @Test
    void createDocument_invalidUuid_returns400() throws Exception {
        mockMvc.perform(post("/clients/not-a-uuid/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DocumentRequest("Title", "Content"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid value for parameter 'id'"));
    }

    @Test
    void createDocument_clientNotFound_returns404() throws Exception {
        var docRequest = new DocumentRequest("Utility Bill", "Some content");
        UUID fakeId = UUID.randomUUID();

        mockMvc.perform(post("/clients/" + fakeId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(docRequest)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Client not found"));
    }

    @Test
    void createDocument_missingTitle_returns400() throws Exception {
        String clientId = createClientAndGetId();
        var docRequest = new DocumentRequest(null, "Some content");

        mockMvc.perform(post("/clients/" + clientId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(docRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    void createDocument_missingContent_returns400() throws Exception {
        String clientId = createClientAndGetId();
        var docRequest = new DocumentRequest("My Doc", null);

        mockMvc.perform(post("/clients/" + clientId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(docRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.content").exists());
    }

    @Test
    void createDocument_duplicateTitle_returns409() throws Exception {
        String clientId = createClientAndGetId();
        var docRequest = new DocumentRequest("Utility Bill", "First version");

        mockMvc.perform(post("/clients/" + clientId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(docRequest)))
                .andExpect(status().isCreated());

        // Same title for same client
        var docRequest2 = new DocumentRequest("Utility Bill", "Second version");

        mockMvc.perform(post("/clients/" + clientId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(docRequest2)))
                .andExpect(status().isConflict());
    }
}
