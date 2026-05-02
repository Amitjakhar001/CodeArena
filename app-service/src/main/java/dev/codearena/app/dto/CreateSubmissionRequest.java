package dev.codearena.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateSubmissionRequest(
    String problemId,
    @NotNull @Positive Integer languageId,
    @NotBlank String sourceCode,
    String customStdin
) {}
