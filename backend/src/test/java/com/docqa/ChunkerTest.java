package com.docqa;

import static org.junit.jupiter.api.Assertions.*;

import com.docqa.rag.Chunker;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkerTest {
    @Test
    void shortTextIsOneChunk() {
        assertEquals(List.of("Hello world."), Chunker.split("  Hello   world. ", 100, 10));
    }

    @Test
    void longTextIsSplitWithOverlapAndNeverExceedsSize() {
        String text = "Sentence number one is here. ".repeat(100);
        List<String> chunks = Chunker.split(text, 200, 40);
        assertTrue(chunks.size() > 5);
        chunks.forEach(c -> assertTrue(c.length() <= 200));
        // overlap: the start of chunk 2 appears at the end of chunk 1
        String head = chunks.get(1).substring(0, 15);
        assertTrue(chunks.get(0).contains(head) || chunks.get(0).endsWith(head.trim()));
    }

    @Test
    void emptyTextGivesNoChunksAndBadConfigIsRejected() {
        assertTrue(Chunker.split("   ", 100, 10).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> Chunker.split("x", 10, 10));
    }
}
