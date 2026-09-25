package dev.erikamendes.support.knowledge.application;

import java.util.List;

public interface EmbeddingClient {
    String model();
    List<double[]> embed(List<String> texts);
}
