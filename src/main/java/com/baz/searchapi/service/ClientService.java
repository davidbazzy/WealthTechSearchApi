package com.baz.searchapi.service;

import com.baz.searchapi.model.dto.ClientRequest;
import com.baz.searchapi.model.dto.ClientResponse;
import com.baz.searchapi.model.entity.Client;
import com.baz.searchapi.repository.ClientRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClientService {

    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    public ClientResponse createClient(ClientRequest request) {
        if (clientRepository.findByEmailIgnoreCase(request.email()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A client with this email already exists");
        }

        Client client = new Client();
        client.setFirstName(request.firstName());
        client.setLastName(request.lastName());
        client.setEmail(request.email());
        client.setDescription(request.description());
        client.setSocialLinks(request.socialLinks());

        client = clientRepository.save(client);
        return toResponse(client);
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
