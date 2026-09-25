package dev.erikamendes.support.knowledge.domain;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TextChunker {
    private static final int CHUNK_SIZE = 900;
    private static final int OVERLAP = 120;

    public List<String> split(String text) {
        int[] codePoints = text.codePoints().toArray();
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < codePoints.length; ) {
            int end = Math.min(start + CHUNK_SIZE, codePoints.length);
            if (end < codePoints.length) {
                for (int candidate = end - 1; candidate > start + CHUNK_SIZE - 100; candidate--) {
                    if (Character.isWhitespace(codePoints[candidate])) {
                        end = candidate + 1;
                        break;
                    }
                }
            }
            String chunk = new String(codePoints, start, end - start).trim();
            if (!chunk.isEmpty()) chunks.add(chunk);
            if (end == codePoints.length) break;
            start = Math.max(start + 1, end - OVERLAP);
        }
        return chunks;
    }
}
