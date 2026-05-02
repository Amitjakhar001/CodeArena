package dev.codearena.app.dto;

import dev.codearena.app.domain.LanguageStat;
import dev.codearena.app.domain.UserStats;

import java.time.Instant;
import java.util.Map;

public record UserStatsResponse(
    long totalSubmissions,
    long acceptedSubmissions,
    Map<String, LanguageStat> byLanguage,
    Instant lastSubmissionAt
) {
    public static UserStatsResponse from(UserStats s) {
        if (s == null) {
            return new UserStatsResponse(0, 0, Map.of(), null);
        }
        return new UserStatsResponse(
            s.getTotalSubmissions(),
            s.getAcceptedSubmissions(),
            s.getByLanguage() == null ? Map.of() : s.getByLanguage(),
            s.getLastSubmissionAt()
        );
    }
}
