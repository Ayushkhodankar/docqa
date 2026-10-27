package com.docqa.rag.vertex;

import com.docqa.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.docqa.rag.EmbeddingClient;

/** Vertex AI text embeddings (text-embedding-005 by default, 768 dims). */
@Component
@ConditionalOnProperty(name = "app.rag.provider", havingValue = "vertex")
public class VertexEmbeddingClient implements EmbeddingClient {
    private final VertexHttp http;
    private final AppProperties.Gcp gcp;

    public VertexEmbeddingClient(VertexHttp http, AppProperties props) {
        this.http = http;
        this.gcp = props.gcp();
    }

    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        return embed(texts, "RETRIEVAL_DOCUMENT");
    }

    @Override
    public float[] embedQuery(String query) {
        return embed(List.of(query), "RETRIEVAL_QUERY").get(0);
    }

    private List<float[]> embed(List<String> texts, String taskType) {
        String url = "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s:predict"
                .formatted(gcp.location(), gcp.projectId(), gcp.location(), gcp.embeddingModel());
        var instances = texts.stream().map(t -> Map.of("content", t, "task_type", taskType)).toList();
        JsonNode res = http.post(url, Map.of("instances", instances, "parameters", Map.of("autoTruncate", true)));
        JsonNode predictions = res == null ? null : res.path("predictions");
        if (predictions == null || predictions.size() != texts.size()) {
            throw new IllegalStateException("Unexpected response from the Vertex AI embeddings API");
        }
        List<float[]> out = new ArrayList<>();
        for (JsonNode p : predictions) {
            JsonNode values = p.path("embeddings").path("values");
            float[] v = new float[values.size()];
            double norm = 0;
            for (int i = 0; i < v.length; i++) {
                v[i] = (float) values.get(i).asDouble();
                norm += v[i] * v[i];
            }
            norm = Math.sqrt(norm); // unit vectors so DOT_PRODUCT == cosine similarity
            if (norm > 0) for (int i = 0; i < v.length; i++) v[i] /= (float) norm;
            out.add(v);
        }
        return out;
    }
}
