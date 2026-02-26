package com.baz.searchapi.controller;

import com.baz.searchapi.model.dto.ClientRequest;
import com.baz.searchapi.model.dto.ClientResponse;
import com.baz.searchapi.model.dto.DocumentRequest;
import com.baz.searchapi.model.dto.DocumentResponse;
import com.baz.searchapi.service.ClientService;
import com.baz.searchapi.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Tag(name = "Clients", description = "Client and document management")
public class ClientController {

    private final ClientService clientService;
    private final DocumentService documentService;

    public ClientController(ClientService clientService, DocumentService documentService) {
        this.clientService = clientService;
        this.documentService = documentService;
    }

    @PostMapping("/clients")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new client")
    @ApiResponse(responseCode = "201", description = "Client created")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "409", description = "Email already exists")
    public ClientResponse createClient(@Valid @RequestBody ClientRequest request) {
        return clientService.createClient(request);
    }

    @PostMapping("/clients/{id}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a document to a client")
    @ApiResponse(responseCode = "201", description = "Document created")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "404", description = "Client not found")
    @ApiResponse(responseCode = "409", description = "Duplicate document title for this client")
    public DocumentResponse createDocument(@PathVariable UUID id,
                                           @Valid @RequestBody DocumentRequest request) {
        return documentService.createDocument(id, request);
    }
}
