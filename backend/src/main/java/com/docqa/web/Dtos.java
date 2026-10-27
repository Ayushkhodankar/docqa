package com.docqa.web;

import com.docqa.domain.Document;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class Dtos {
    private Dtos() {}

    public record AuthRequest(
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email address") String email,
            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 100, message = "Password must be 8 to 100 characters") String password) {}

    public record AuthResponse(String token, String email) {}

    public record QueryRequest(
            @NotBlank(message = "Enter a question")
            @Size(max = 1000, message = "Questions are limited to 1000 characters") String question) {}

    public record Source(int n, Long documentId, String filename, Integer page, String snippet, double score) {}

    public record QueryResponse(String answer, List<Source> sources) {}

    public record DocumentDto(Long id, String filename, String status, int chunkCount, Instant createdAt, String error) {
        public static DocumentDto from(Document d) {
            return new DocumentDto(d.getId(), d.getFilename(), d.getStatus().name(), d.getChunkCount(),
                    d.getCreatedAt(), d.getError());
        }
    }
}
