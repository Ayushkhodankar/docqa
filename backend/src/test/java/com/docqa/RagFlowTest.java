package com.docqa;

import static org.hamcrest.Matchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "app.rag.provider=local"})
@AutoConfigureMockMvc
class RagFlowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private String register(String email) throws Exception {
        String body = om.writeValueAsString(Map.of("email", email, "password", "password123"));
        String res = mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + om.readTree(res).get("token").asText();
    }

    private void upload(String auth, String name, String text) throws Exception {
        var file = new MockMultipartFile("file", name, "text/plain", text.getBytes());
        mvc.perform(multipart("/api/documents").file(file).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));
    }

    private String ask(String auth, String q) throws Exception {
        return mvc.perform(post("/api/query").header("Authorization", auth).contentType(APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("question", q))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void protectedEndpointsRequireAToken() throws Exception {
        mvc.perform(get("/api/documents")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/documents").header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateRegistrationAndBadLoginAreRejected() throws Exception {
        register("dup@example.com");
        String body = om.writeValueAsString(Map.of("email", "dup@example.com", "password", "password123"));
        mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        String bad = om.writeValueAsString(Map.of("email", "dup@example.com", "password", "wrong-password"));
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content(bad)).andExpect(status().isUnauthorized());
    }

    @Test
    void uploadThenAskReturnsCitedAnswer() throws Exception {
        String auth = register("alice@example.com");
        upload(auth, "policy.txt", "Our refund window is 30 days from delivery. Shipping takes five business days. "
                + "Support is open on weekdays.");
        mvc.perform(post("/api/query").header("Authorization", auth).contentType(APPLICATION_JSON)
                        .content("{\"question\":\"What is the refund window?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", containsString("30 days")))
                .andExpect(jsonPath("$.sources[0].filename").value("policy.txt"))
                .andExpect(jsonPath("$.sources[0].n").value(1));
    }

    @Test
    void usersNeverSeeEachOthersDocuments() throws Exception {
        String alice = register("alice2@example.com");
        String bob = register("bob@example.com");
        upload(alice, "secret.txt", "The launch codename is Bluebird and the budget is large.");
        mvc.perform(get("/api/documents").header("Authorization", bob)).andExpect(jsonPath("$", hasSize(0)));
        String res = ask(bob, "What is the launch codename?");
        org.junit.jupiter.api.Assertions.assertFalse(res.contains("Bluebird"));
    }

    @Test
    void rejectsUnsupportedFileTypes() throws Exception {
        String auth = register("carol@example.com");
        var file = new MockMultipartFile("file", "virus.exe", "application/octet-stream", "x".getBytes());
        mvc.perform(multipart("/api/documents").file(file).header("Authorization", auth))
                .andExpect(status().isUnsupportedMediaType());
    }
}
