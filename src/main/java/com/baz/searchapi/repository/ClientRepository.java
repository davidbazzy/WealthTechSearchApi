package com.baz.searchapi.repository;

import com.baz.searchapi.model.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {
    Optional<Client> findByEmailIgnoreCase(String email);
}
