package com.docqa.web;

import com.docqa.rag.IngestionService;
import com.docqa.rag.TextExtractor;
import com.docqa.repo.DocumentRepository;
import com.docqa.security.AuthUser;
import com.docqa.web.Dtos.DocumentDto;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {
    private final IngestionService ingestion;
    private final DocumentRepository documents;

    @GetMapping
    public List<DocumentDto> list(@AuthenticationPrincipal AuthUser user) {
        return documents.findByOwnerIdOrderByCreatedAtDesc(user.id()).stream().map(DocumentDto::from).toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentDto upload(@AuthenticationPrincipal AuthUser user, @RequestParam("file") MultipartFile file)
            throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is empty");
        }
        String name = safeName(file.getOriginalFilename());
        if (!TextExtractor.isSupported(name)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Upload a PDF, TXT or MD file");
        }
        return DocumentDto.from(ingestion.ingest(user.id(), name, file.getBytes()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        if (!ingestion.delete(user.id(), id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
    }

    private static String safeName(String original) {
        if (original == null || original.isBlank()) return "document";
        String n = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1).trim();
        return n.isEmpty() ? "document" : (n.length() > 200 ? n.substring(n.length() - 200) : n);
    }
}
