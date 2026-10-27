package com.docqa.rag;

import java.util.List;

public interface VectorStore {
    record Record(String id, Long ownerId, float[] vector) {}
    record Match(String id, double score) {}

    void upsert(List<Record> records);

    /** Nearest neighbours restricted to one owner's datapoints, best first. */
    List<Match> search(float[] query, Long ownerId, int k);

    void delete(List<String> ids);
}
