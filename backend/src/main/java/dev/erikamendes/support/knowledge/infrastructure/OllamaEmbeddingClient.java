package dev.erikamendes.support.knowledge.infrastructure;

import dev.erikamendes.support.knowledge.application.EmbeddingClient;
import dev.erikamendes.support.knowledge.application.EmbeddingUnavailableException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(name = "app.embeddings.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaEmbeddingClient implements EmbeddingClient {
    private final RestClient client;
    private final String model;

    public OllamaEmbeddingClient(@Value("${app.embeddings.base-url}") String baseUrl,
                                 @Value("${app.embeddings.model}") String model) {
        if (model.isBlank() || model.length() > 120) throw new IllegalArgumentException("Invalid embedding model");
        this.model = model;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(90));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Override
    public String model() { return model; }

    @Override
    public List<double[]> embed(List<String> texts) {
        if (texts.isEmpty()) return List.of();
        try {
            EmbedResponse response = client.post().uri("/api/embed")
                    .body(Map.of("model", model, "input", texts, "truncate", false))
                    .retrieve().body(EmbedResponse.class);
            if (response == null || response.embeddings() == null || response.embeddings().size() != texts.size()) {
                throw new EmbeddingUnavailableException();
            }
            return response.embeddings().stream().map(values -> {
                if (values == null) throw new EmbeddingUnavailableException();
                double[] vector = new double[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    if (values.get(i) == null) throw new EmbeddingUnavailableException();
                    vector[i] = values.get(i);
                }
                return vector;
            }).toList();
        } catch (RestClientException exception) {
            // Do not expose provider response bodies, which may include input text.
            throw new EmbeddingUnavailableException();
        }
    }

    private record EmbedResponse(List<List<Double>> embeddings) { }
}
