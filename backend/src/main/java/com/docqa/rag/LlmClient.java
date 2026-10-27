package com.docqa.rag;

import java.util.List;

public interface LlmClient {
    record Passage(int n, String filename, Integer page, String text) {}

    /** Answers the question using only the supplied passages, citing them as [n]. */
    String answer(String question, List<Passage> passages);
}
