package com.docqa.rag.local;

import com.docqa.domain.Chunk;
import com.docqa.rag.EmbeddingClient;
import com.docqa.rag.VectorStore;
import com.docqa.repo.ChunkRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** The in-memory index is lost on restart, so rebuild it from persisted chunks. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rag.provider", havingValue = "local", matchIfMissing = true)
public class LocalReindexer implements ApplicationRunner {
    private final ChunkRepository chunks;
    private final EmbeddingClient embeddings;
    private final VectorStore vectors;

    @Override
    public void run(ApplicationArguments args) {
        List<Chunk> all = chunks.findAll();
        if (all.isEmpty()) return;
        List<float[]> vecs = embeddings.embedDocuments(all.stream().map(Chunk::getText).toList());
        for (int i = 0; i < all.size(); i++) {
            Chunk c = all.get(i);
            vectors.upsert(List.of(new VectorStore.Record(c.getVectorId(), c.getOwnerId(), vecs.get(i))));
        }
    }
}
