package com.docqa.rag.vertex;

import com.docqa.config.AppProperties;
import com.docqa.rag.VectorStore;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Vertex AI Vector Search. The index must be created with STREAM_UPDATE so datapoints can be
 * upserted/removed at runtime (see infra/setup-gcp.sh). Each datapoint carries an "owner"
 * restrict so a query only ever searches the caller's own documents.
 */
@Component
@ConditionalOnProperty(name = "app.rag.provider", havingValue = "vertex")
public class VertexVectorStore implements VectorStore {
    private final VertexHttp http;
    private final AppProperties.Gcp gcp;

    public VertexVectorStore(VertexHttp http, AppProperties props) {
        this.http = http;
        this.gcp = props.gcp();
    }

    private String indexUrl(String action) {
        return "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/indexes/%s:%s"
                .formatted(gcp.location(), gcp.projectId(), gcp.location(), gcp.indexId(), action);
    }

    private static List<Map<String, Object>> ownerRestrict(Long ownerId) {
        return List.of(Map.of("namespace", "owner", "allowList", List.of(String.valueOf(ownerId))));
    }

    @Override
    public void upsert(List<Record> records) {
        var datapoints = records.stream().map(r -> Map.of(
                "datapointId", r.id(),
                "featureVector", r.vector(),
                "restricts", ownerRestrict(r.ownerId()))).toList();
        http.post(indexUrl("upsertDatapoints"), Map.of("datapoints", datapoints));
    }

    @Override
    public List<Match> search(float[] query, Long ownerId, int k) {
        String url = "https://%s/v1/projects/%s/locations/%s/indexEndpoints/%s:findNeighbors"
                .formatted(gcp.publicEndpointDomain(), gcp.projectId(), gcp.location(), gcp.indexEndpointId());
        Map<String, Object> body = Map.of(
                "deployedIndexId", gcp.deployedIndexId(),
                "queries", List.of(Map.of(
                        "datapoint", Map.of("featureVector", query, "restricts", ownerRestrict(ownerId)),
                        "neighborCount", k)));
        JsonNode res = http.post(url, body);
        List<Match> out = new ArrayList<>();
        if (res == null) return out;
        for (JsonNode n : res.path("nearestNeighbors").path(0).path("neighbors")) {
            out.add(new Match(n.path("datapoint").path("datapointId").asText(), n.path("distance").asDouble()));
        }
        return out;
    }

    @Override
    public void delete(List<String> ids) {
        http.post(indexUrl("removeDatapoints"), Map.of("datapointIds", ids));
    }
}
