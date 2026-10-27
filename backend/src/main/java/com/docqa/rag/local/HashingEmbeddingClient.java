package com.docqa.rag.local;

import com.docqa.rag.EmbeddingClient;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Offline bag-of-words "embedding" (feature hashing) so the app runs without GCP.
 * Good enough for demos and tests; use the vertex provider for real semantic search.
 */
@Component
@ConditionalOnProperty(name = "app.rag.provider", havingValue = "local", matchIfMissing = true)
public class HashingEmbeddingClient implements EmbeddingClient {
    static final int DIM = 512;
    private static final Set<String> STOP = Set.of("the", "a", "an", "and", "or", "of", "to", "in", "on", "is", "are",
            "was", "were", "it", "this", "that", "for", "with", "as", "by", "what", "how", "why", "do", "does", "be");

    public static List<String> tokens(String text) {
        return java.util.Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(t -> t.length() > 1 && !STOP.contains(t)).toList();
    }

    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }

    @Override
    public float[] embedQuery(String query) {
        return embed(query);
    }

    private float[] embed(String text) {
        float[] v = new float[DIM];
        for (String t : tokens(text)) v[Math.floorMod(t.hashCode(), DIM)] += 1f;
        double norm = 0;
        for (float x : v) norm += x * x;
        norm = Math.sqrt(norm);
        if (norm > 0) for (int i = 0; i < DIM; i++) v[i] /= (float) norm;
        return v;
    }
}
