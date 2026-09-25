package dev.erikamendes.support.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import dev.erikamendes.support.knowledge.infrastructure.OllamaEmbeddingClient;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class OllamaEmbeddingClientTests {
    @Test
    void sendsBatchRequestAndReadsEmbeddingResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/embed", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(body).contains("nomic-embed-text:v1.5", "pagamento", "\"truncate\":false");
            String vector = IntStream.range(0, 768).mapToObj(i -> i == 0 ? "1.0" : "0.0")
                    .collect(Collectors.joining(",", "[", "]"));
            byte[] response = ("{\"model\":\"nomic-embed-text:v1.5\",\"embeddings\":[" + vector + "]}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            var client = new OllamaEmbeddingClient("http://127.0.0.1:" + server.getAddress().getPort(),
                    "nomic-embed-text:v1.5");
            var result = client.embed(java.util.List.of("pagamento"));
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).hasSize(768);
            assertThat(result.get(0)[0]).isEqualTo(1.0);
        } finally {
            server.stop(0);
        }
    }
}
