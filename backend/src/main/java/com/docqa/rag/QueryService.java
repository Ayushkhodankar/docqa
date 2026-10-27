package com.docqa.rag;

import com.docqa.config.AppProperties;
import com.docqa.domain.Chunk;
import com.docqa.domain.Document;
import com.docqa.repo.ChunkRepository;
import com.docqa.repo.DocumentRepository;
import com.docqa.web.Dtos.QueryResponse;
import com.docqa.web.Dtos.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Question -> embed -> vector search -> load passages -> grounded LLM answer with citations. */
@Service
@RequiredArgsConstructor
public class QueryService {
    private final EmbeddingClient embeddings;
    private final VectorStore vectors;
    private final LlmClient llm;
    private final ChunkRepository chunks;
    private final DocumentRepository documents;
    private final AppProperties props;

    public QueryResponse query(Long userId, String question) {
        List<VectorStore.Match> matches = vectors.search(embeddings.embedQuery(question), userId, props.rag().topK());

        // Ownership is re-checked in SQL (owner_id), so a vector-index mistake can never leak another user's text.
        Map<String, Chunk> byVectorId = chunks
                .findByVectorIdInAndOwnerId(matches.stream().map(VectorStore.Match::id).toList(), userId)
                .stream().collect(Collectors.toMap(Chunk::getVectorId, Function.identity()));
        Map<Long, String> names = documents.findAllById(
                byVectorId.values().stream().map(Chunk::getDocumentId).distinct().toList())
                .stream().collect(Collectors.toMap(Document::getId, Document::getFilename));

        List<LlmClient.Passage> passages = new ArrayList<>();
        List<Source> sources = new ArrayList<>();
        for (VectorStore.Match m : matches) {
            Chunk c = byVectorId.get(m.id());
            if (c == null) continue;
            int n = passages.size() + 1;
            String file = names.getOrDefault(c.getDocumentId(), "unknown");
            passages.add(new LlmClient.Passage(n, file, c.getPage(), c.getText()));
            sources.add(new Source(n, c.getDocumentId(), file, c.getPage(), c.getText(), m.score()));
        }
        if (passages.isEmpty()) {
            return new QueryResponse("I couldn't find anything relevant in your documents. "
                    + "Upload a document first, or try rephrasing your question.", List.of());
        }
        return new QueryResponse(llm.answer(question, passages), sources);
    }
}
