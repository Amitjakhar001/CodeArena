package dev.codearena.app.dto;

import dev.codearena.app.domain.Difficulty;
import dev.codearena.app.domain.Problem;
import dev.codearena.app.domain.SampleTestCase;

import java.util.List;

public record ProblemDetailResponse(
    String id,
    String slug,
    String title,
    String description,
    Difficulty difficulty,
    List<SampleTestCase> sampleTestCases,
    List<Integer> supportedLanguages
) {
    public static ProblemDetailResponse from(Problem p) {
        // Hide hidden test cases from the detail response — they're for grading only.
        List<SampleTestCase> visible = p.getSampleTestCases() == null ? List.of()
            : p.getSampleTestCases().stream().filter(tc -> !tc.isHidden()).toList();
        return new ProblemDetailResponse(
            p.getId().toHexString(),
            p.getSlug(),
            p.getTitle(),
            p.getDescription(),
            p.getDifficulty(),
            visible,
            p.getSupportedLanguages()
        );
    }
}
