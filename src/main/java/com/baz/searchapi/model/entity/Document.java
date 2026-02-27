package com.baz.searchapi.model.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "documents", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"client_id", "title"})
})
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Chunk> chunks = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Document() {}

    public Document(UUID id, Client client, String title, String content, LocalDateTime createdAt, List<Chunk> chunks) {
        this.id = id;
        this.client = client;
        this.title = title;
        this.content = content;
        this.createdAt = createdAt;
        this.chunks = chunks != null ? chunks : new ArrayList<>();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<Chunk> getChunks() { return chunks; }
    public void setChunks(List<Chunk> chunks) { this.chunks = chunks; }
}
