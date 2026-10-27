package com.docqa.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "documents", indexes = @Index(columnList = "ownerId"))
@Getter @Setter @NoArgsConstructor
public class Document {
    public enum Status { PROCESSING, READY, FAILED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private String filename;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PROCESSING;

    private int chunkCount;

    @Column(length = 500)
    private String error;

    private Instant createdAt = Instant.now();

    public Document(Long ownerId, String filename) {
        this.ownerId = ownerId;
        this.filename = filename;
    }
}
