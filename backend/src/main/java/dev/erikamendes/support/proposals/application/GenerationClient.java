package dev.erikamendes.support.proposals.application;

import dev.erikamendes.support.proposals.domain.ResolutionProposal.Source;
import java.util.List;

public interface GenerationClient {
    String model();
    String generate(String title, String description, List<Source> sources);
}
