package com.docqa.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Rag rag, Gcp gcp) {
    public record Jwt(String secret, long expirationMinutes) {}
    public record Cors(List<String> allowedOrigins) {}
    public record Rag(String provider, int chunkSize, int chunkOverlap, int topK) {}
    public record Gcp(String projectId, String location, String embeddingModel, int embeddingDimensions,
                      String generationModel, String indexId, String indexEndpointId,
                      String deployedIndexId, String publicEndpointDomain) {}
}
