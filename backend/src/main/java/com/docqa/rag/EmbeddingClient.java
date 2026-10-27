package com.docqa.rag;

import java.util.List;

public interface EmbeddingClient {
    /** Embeds passages for indexing (order-preserving). */
    List<float[]> embedDocuments(List<String> texts);

    /** Embeds a user question for retrieval. */
    float[] embedQuery(String query);
}
