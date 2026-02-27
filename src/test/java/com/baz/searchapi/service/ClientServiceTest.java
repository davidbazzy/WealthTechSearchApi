package com.baz.searchapi.service;

import com.baz.searchapi.model.dto.ClientRequest;
import com.baz.searchapi.model.dto.ClientResponse;
import com.baz.searchapi.model.entity.Client;
import com.baz.searchapi.repository.ClientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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

        when(clientRepository.saveAndFlush(any())).thenReturn(saved);

        ClientResponse response = clientService.createClient(request);

        assertEquals("John", response.firstName());
        assertEquals("Doe", response.lastName());
        assertEquals("john@example.com", response.email());
        assertEquals("Senior advisor", response.description());
        assertNotNull(response.id());
    }

    @Test
    void createClient_duplicateEmail_throws409() {
        when(clientRepository.saveAndFlush(any())).thenThrow(
                new DataIntegrityViolationException("clients_email_key"));

        var request = new ClientRequest("John", "Doe", "john@example.com", null, null);

        var ex = assertThrows(ResponseStatusException.class,
                () -> clientService.createClient(request));
        assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());
        assertEquals("A client with this email already exists", ex.getReason());
    }

    @Test
    void createClient_emailIsNormalizedToLowercase() {
        var request = new ClientRequest("John", "Doe", "JOHN@EXAMPLE.COM", null, null);
        var saved = new Client(UUID.randomUUID(), "John", "Doe", "john@example.com", null, null);
        when(clientRepository.saveAndFlush(any())).thenReturn(saved);

        clientService.createClient(request);

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).saveAndFlush(captor.capture());
        assertEquals("john@example.com", captor.getValue().getEmail());
    }
}
