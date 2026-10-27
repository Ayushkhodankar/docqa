package com.docqa.repo;

import com.docqa.domain.Document;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    Optional<Document> findByIdAndOwnerId(Long id, Long ownerId);
}
