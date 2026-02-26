package com.baz.searchapi.model.entity;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "chunks")
public class Chunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "CLOB")
    private String text;

    @Lob
    @Column(nullable = false)
    private byte[] embedding;

    public Chunk() {}

    public Chunk(UUID id, Document document, int chunkIndex, String text, byte[] embedding) {
        this.id = id;
        this.document = document;
        this.chunkIndex = chunkIndex;
        this.text = text;
        this.embedding = embedding;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }

    public int getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public byte[] getEmbedding() { return embedding; }
    public void setEmbedding(byte[] embedding) { this.embedding = embedding; }
}
