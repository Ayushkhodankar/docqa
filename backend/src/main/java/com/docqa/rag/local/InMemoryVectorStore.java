package com.docqa.rag.local;

import com.docqa.rag.VectorStore;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Brute-force cosine search over unit vectors. Rebuilt from the database on startup. */
@Component
@ConditionalOnProperty(name = "app.rag.provider", havingValue = "local", matchIfMissing = true)
public class InMemoryVectorStore implements VectorStore {
    private final Map<String, Record> data = new ConcurrentHashMap<>();

    @Override
    public void upsert(List<Record> records) {
        records.forEach(r -> data.put(r.id(), r));
    }

    @Override
    public List<Match> search(float[] q, Long ownerId, int k) {
        return data.values().stream()
                .filter(r -> r.ownerId().equals(ownerId))
                .map(r -> new Match(r.id(), dot(q, r.vector())))
                .filter(m -> m.score() > 0)
                .sorted(Comparator.comparingDouble(Match::score).reversed())
                .limit(k).toList();
    }

    @Override
    public void delete(List<String> ids) {
        ids.forEach(data::remove);
    }

    private static double dot(float[] a, float[] b) {
        double s = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) s += a[i] * b[i];
        return s;
    }
}
