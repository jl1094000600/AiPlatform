package com.aipal.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RagChunkerTest {

    private final RagChunker chunker = new RagChunker();

    @Test
    void chunksContentWithOverlap() {
        List<String> chunks = chunker.chunk("abcdefghij", 4, 1);

        assertEquals(List.of("abcd", "defg", "ghij"), chunks);
    }

    @Test
    void returnsEmptyForBlankContent() {
        assertEquals(List.of(), chunker.chunk("   ", 100, 10));
    }

    @Test
    void rejectsInvalidOverlap() {
        assertThrows(IllegalArgumentException.class, () -> chunker.chunk("abc", 3, 3));
    }
}
