package com.docqa.rag.vertex;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Authenticated JSON POSTs to Google APIs using Application Default Credentials (the Cloud Run service account). */
@Component
@ConditionalOnProperty(name = "app.rag.provider", havingValue = "vertex")
public class VertexHttp {
    private final GoogleCredentials credentials;
    private final RestClient client = RestClient.create();

    public VertexHttp() throws IOException {
        this.credentials = GoogleCredentials.getApplicationDefault()
                .createScoped("https://www.googleapis.com/auth/cloud-platform");
    }

    public JsonNode post(String url, Object body) {
        String token;
        try {
            credentials.refreshIfExpired();
            token = credentials.getAccessToken().getTokenValue();
        } catch (IOException e) {
            throw new IllegalStateException("Could not obtain Google access token: " + e.getMessage(), e);
        }
        return client.post().uri(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }
}
