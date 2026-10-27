package com.docqa.rag.local;

import com.docqa.rag.LlmClient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Offline "LLM": quotes the sentences that best overlap the question, with [n] citations. */
@Component
@ConditionalOnProperty(name = "app.rag.provider", havingValue = "local", matchIfMissing = true)
public class ExtractiveLlmClient implements LlmClient {
    private record Scored(int n, String sentence, long score) {}

    @Override
    public String answer(String question, List<Passage> passages) {
        Set<String> q = new HashSet<>(HashingEmbeddingClient.tokens(question));
        List<Scored> scored = new ArrayList<>();
        for (Passage p : passages) {
            for (String s : p.text().split("(?<=[.!?])\\s+")) {
                long score = HashingEmbeddingClient.tokens(s).stream().filter(q::contains).count();
                if (score > 0) scored.add(new Scored(p.n(), s.trim(), score));
            }
        }
        scored.sort(Comparator.comparingLong(Scored::score).reversed());
        if (scored.isEmpty()) {
            return "The retrieved passages don't clearly answer that. See the sources below. [1]";
        }
        StringBuilder sb = new StringBuilder("Here is what your documents say (offline demo mode, no LLM):\n\n");
        scored.stream().limit(3).forEach(s -> sb.append("• ").append(s.sentence()).append(" [").append(s.n()).append("]\n"));
        return sb.toString().trim();
    }
}
