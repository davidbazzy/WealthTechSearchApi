package com.baz.searchapi.controller;

import tools.jackson.databind.json.JsonMapper;
import com.baz.searchapi.model.dto.DocumentRequest;
import com.baz.searchapi.model.dto.DocumentResponse;
import com.baz.searchapi.service.ClientService;
import com.baz.searchapi.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClientController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper objectMapper;

    @MockitoBean
    private ClientService clientService;

    @MockitoBean
    private DocumentService documentService;

    @Test
    void createDocument_happyPath_returns201() throws Exception {
        UUID clientId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        var request = new DocumentRequest("Utility Bill", "This is a utility bill.");

        when(documentService.createDocument(eq(clientId), any())).thenReturn(
                new DocumentResponse(docId, clientId, "Utility Bill",
                        "This is a utility bill.", LocalDateTime.now()));

        mockMvc.perform(post("/clients/" + clientId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.client_id").value(clientId.toString()))
                .andExpect(jsonPath("$.title").value("Utility Bill"))
                .andExpect(jsonPath("$.content").value("This is a utility bill."))
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
        UUID fakeId = UUID.randomUUID();

        when(documentService.createDocument(eq(fakeId), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));

        mockMvc.perform(post("/clients/" + fakeId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DocumentRequest("Title", "Content"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Client not found"));
    }

    @Test
    void createDocument_missingTitle_returns400() throws Exception {
        UUID clientId = UUID.randomUUID();

        mockMvc.perform(post("/clients/" + clientId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DocumentRequest(null, "Some content"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    void createDocument_missingContent_returns400() throws Exception {
        UUID clientId = UUID.randomUUID();

        mockMvc.perform(post("/clients/" + clientId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DocumentRequest("My Doc", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.content").exists());
    }

    @Test
    void createDocument_duplicateTitle_returns409() throws Exception {
        UUID clientId = UUID.randomUUID();

        when(documentService.createDocument(eq(clientId), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                        "A document with this title already exists for this client"));

        mockMvc.perform(post("/clients/" + clientId + "/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DocumentRequest("Utility Bill", "Content"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("A document with this title already exists for this client"));
    }
}
