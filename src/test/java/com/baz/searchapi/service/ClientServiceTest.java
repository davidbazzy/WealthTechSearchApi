package com.baz.searchapi.service;

import com.baz.searchapi.model.dto.ClientRequest;
import com.baz.searchapi.model.dto.ClientResponse;
import com.baz.searchapi.model.entity.Client;
import com.baz.searchapi.repository.ClientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private ClientService clientService;

    @Test
    void createClient_newEmail_savesAndReturnsResponse() {
        var request = new ClientRequest("John", "Doe", "john@example.com", "Senior advisor", null);
        var saved = new Client(UUID.randomUUID(), "John", "Doe", "john@example.com", "Senior advisor", null);

        when(clientRepository.existsByEmailIgnoreCase("john@example.com")).thenReturn(false);
        when(clientRepository.save(any())).thenReturn(saved);

        ClientResponse response = clientService.createClient(request);

        assertEquals("John", response.firstName());
        assertEquals("Doe", response.lastName());
        assertEquals("john@example.com", response.email());
        assertEquals("Senior advisor", response.description());
        assertNotNull(response.id());
    }

    @Test
    void createClient_duplicateEmail_throws409() {
        when(clientRepository.existsByEmailIgnoreCase("john@example.com")).thenReturn(true);

        var request = new ClientRequest("John", "Doe", "john@example.com", null, null);

        var ex = assertThrows(ResponseStatusException.class, () -> clientService.createClient(request));
        assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());
        assertEquals("A client with this email already exists", ex.getReason());
    }

    @Test
    void createClient_emailIsCaseInsensitive() {
        when(clientRepository.existsByEmailIgnoreCase("john@example.com")).thenReturn(true);

        var request = new ClientRequest("John", "Doe", "john@example.com", null, null);

        assertThrows(ResponseStatusException.class, () -> clientService.createClient(request));
    }
}
