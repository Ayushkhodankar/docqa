package com.docqa.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "chunks", indexes = {
        @Index(columnList = "documentId"),
        @Index(columnList = "vectorId", unique = true)})
@Getter @Setter @NoArgsConstructor
public class Chunk {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private Long documentId;
    @Column(nullable = false) private Long ownerId;
    private int chunkIndex;
    /** 1-based page number for PDFs; null for plain text. */
    private Integer page;
    /** ID of this chunk's datapoint in the vector index. */
    @Column(nullable = false) private String vectorId;
    @Column(nullable = false, length = 8000) private String text;

    public Chunk(Long documentId, Long ownerId, int chunkIndex, Integer page, String text) {
        this.documentId = documentId;
        this.ownerId = ownerId;
        this.chunkIndex = chunkIndex;
        this.page = page;
        this.text = text;
        this.vectorId = "d" + documentId + "-c" + chunkIndex;
    }
}
