package dev.codearena.app.dto;

import dev.codearena.app.domain.Difficulty;
import dev.codearena.app.domain.Problem;

import java.util.List;

public record ProblemSummaryResponse(
    String id,
    String slug,
    String title,
    Difficulty difficulty,
    List<Integer> supportedLanguages
) {
    public static ProblemSummaryResponse from(Problem p) {
        return new ProblemSummaryResponse(
            p.getId().toHexString(),
            p.getSlug(),
            p.getTitle(),
            p.getDifficulty(),
            p.getSupportedLanguages()
        );
    }
}
