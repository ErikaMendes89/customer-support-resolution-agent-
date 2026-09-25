package dev.erikamendes.support.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.erikamendes.support.knowledge.domain.TextChunker;
import org.junit.jupiter.api.Test;

class TextChunkerTests {
    @Test
    void longUnicodeTextIsSplitWithoutBreakingSurrogatePairs() {
        String text = "🌿 reserva pagamento ".repeat(130);
        var chunks = new TextChunker().split(text);
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.codePointCount(0, chunk.length())).isLessThanOrEqualTo(900);
            assertThat(chunk).doesNotContain("\uFFFD");
            for (int i = 0; i < chunk.length(); i++) {
                if (Character.isHighSurrogate(chunk.charAt(i))) {
                    assertThat(i + 1).isLessThan(chunk.length());
                    assertThat(Character.isLowSurrogate(chunk.charAt(++i))).isTrue();
                }
            }
        });
        assertThat(chunks.get(0)).startsWith("🌿");
        assertThat(chunks.get(chunks.size() - 1)).endsWith("pagamento");
    }
}
