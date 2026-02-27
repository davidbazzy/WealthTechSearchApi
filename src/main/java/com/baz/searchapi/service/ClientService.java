package com.baz.searchapi.service;

import com.baz.searchapi.model.dto.ClientRequest;
import com.baz.searchapi.model.dto.ClientResponse;
import com.baz.searchapi.model.dto.SearchResultItem;
import com.baz.searchapi.model.entity.Client;
import com.baz.searchapi.repository.ClientRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ClientService {

    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Transactional
    public ClientResponse createClient(ClientRequest request) {
        Client client = new Client();
        client.setFirstName(request.firstName());
        client.setLastName(request.lastName());
        client.setEmail(request.email().toLowerCase());
        client.setDescription(request.description());
        client.setSocialLinks(request.socialLinks());

        try {
            client = clientRepository.saveAndFlush(client);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A client with this email already exists");
        }
        return toResponse(client);
    }

    public List<SearchResultItem> searchClients(String query) {
        return clientRepository.fullTextSearch(query).stream()
                .<SearchResultItem>map(client -> SearchResultItem.fromClient(toResponse(client)))
                .toList();
    }

    public ClientResponse toResponse(Client client) {
        return new ClientResponse(
                client.getId(),
                client.getFirstName(),
                client.getLastName(),
                client.getEmail(),
                client.getDescription(),
                client.getSocialLinks()
        );
    }
}
