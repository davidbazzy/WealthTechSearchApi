package com.baz.searchapi.model.entity;

import com.baz.searchapi.config.VectorConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

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

    @Column(nullable = false)
    private String text;

    @Convert(converter = VectorConverter.class)
    @ColumnTransformer(write = "?::vector")
    @Column(columnDefinition = "vector(384)", nullable = false)
    private float[] embedding;

    public Chunk() {}

    public Chunk(UUID id, Document document, int chunkIndex, String text, float[] embedding) {
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

    public float[] getEmbedding() { return embedding; }
    public void setEmbedding(float[] embedding) { this.embedding = embedding; }
}
