package com.docqa.rag;

import java.util.ArrayList;
import java.util.List;

/** Splits text into overlapping chunks, preferring sentence and then word boundaries. */
public final class Chunker {
    private Chunker() {}

    public static List<String> split(String raw, int size, int overlap) {
        if (size <= 0 || overlap < 0 || overlap >= size) {
            throw new IllegalArgumentException("Require size > 0 and 0 <= overlap < size");
        }
        String t = raw == null ? "" : raw.replaceAll("\\s+", " ").trim();
        List<String> out = new ArrayList<>();
        int start = 0;
        while (start < t.length()) {
            int end = Math.min(start + size, t.length());
            if (end < t.length()) {
                int sentence = t.lastIndexOf(". ", end);
                int space = t.lastIndexOf(' ', end);
                if (sentence > start + size / 2) end = sentence + 1;
                else if (space > start + size / 2) end = space;
            }
            String chunk = t.substring(start, end).trim();
            if (!chunk.isEmpty()) out.add(chunk);
            if (end >= t.length()) break;
            start = Math.max(end - overlap, start + 1);
        }
        return out;
    }
}
