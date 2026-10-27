package com.docqa.repo;

import com.docqa.domain.Chunk;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ChunkRepository extends JpaRepository<Chunk, Long> {
    List<Chunk> findByDocumentId(Long documentId);
    List<Chunk> findByVectorIdInAndOwnerId(Collection<String> vectorIds, Long ownerId);

    @Transactional
    void deleteByDocumentId(Long documentId);
}
