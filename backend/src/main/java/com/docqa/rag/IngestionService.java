package com.docqa.rag;

import com.docqa.config.AppProperties;
import com.docqa.domain.Chunk;
import com.docqa.domain.Document;
import com.docqa.repo.ChunkRepository;
import com.docqa.repo.DocumentRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Upload -> extract text -> chunk -> embed -> index in the vector store. */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {
    private static final int EMBED_BATCH = 20;

    private final DocumentRepository documents;
    private final ChunkRepository chunks;
    private final EmbeddingClient embeddings;
    private final VectorStore vectors;
    private final TextExtractor extractor;
    private final AppProperties props;

    public Document ingest(Long ownerId, String filename, byte[] bytes) {
        Document doc = documents.save(new Document(ownerId, filename));
        try {
            List<Chunk> toSave = new ArrayList<>();
            int position = 0;
            for (var page : extractor.extract(filename, bytes)) {
                for (String text : Chunker.split(page.text(), props.rag().chunkSize(), props.rag().chunkOverlap())) {
                    toSave.add(new Chunk(doc.getId(), ownerId, position++, page.page(), text));
                }
            }
            if (toSave.isEmpty()) {
                throw new IllegalStateException("No readable text found. Scanned (image-only) PDFs are not supported.");
            }
            List<Chunk> saved = chunks.saveAll(toSave);
            for (int i = 0; i < saved.size(); i += EMBED_BATCH) {
                List<Chunk> batch = saved.subList(i, Math.min(i + EMBED_BATCH, saved.size()));
                List<float[]> vecs = embeddings.embedDocuments(batch.stream().map(Chunk::getText).toList());
                List<VectorStore.Record> records = new ArrayList<>();
                for (int j = 0; j < batch.size(); j++) {
                    records.add(new VectorStore.Record(batch.get(j).getVectorId(), ownerId, vecs.get(j)));
                }
                vectors.upsert(records);
            }
            doc.setChunkCount(saved.size());
            doc.setStatus(Document.Status.READY);
        } catch (Exception e) {
            log.warn("Ingestion failed for document {}: {}", doc.getId(), e.toString());
            cleanup(doc.getId());
            doc.setStatus(Document.Status.FAILED);
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            doc.setError(msg.length() > 450 ? msg.substring(0, 450) : msg);
        }
        return documents.save(doc);
    }

    public boolean delete(Long ownerId, Long documentId) {
        return documents.findByIdAndOwnerId(documentId, ownerId).map(doc -> {
            cleanup(doc.getId());
            documents.delete(doc);
            return true;
        }).orElse(false);
    }

    private void cleanup(Long documentId) {
        try {
            List<String> ids = chunks.findByDocumentId(documentId).stream().map(Chunk::getVectorId).toList();
            if (!ids.isEmpty()) vectors.delete(ids);
        } catch (Exception e) {
            log.warn("Vector cleanup failed for document {}: {}", documentId, e.toString());
        }
        chunks.deleteByDocumentId(documentId);
    }
}
