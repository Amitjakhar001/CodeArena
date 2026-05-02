package dev.codearena.app.service;

import dev.codearena.app.domain.LanguageStat;
import dev.codearena.app.domain.SubmissionStatus;
import dev.codearena.app.domain.UserStats;
import dev.codearena.app.repository.UserStatsRepository;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;

@Service
public class UserStatsService {

    private static final Logger log = LoggerFactory.getLogger(UserStatsService.class);

    private final UserStatsRepository repository;

    public UserStatsService(UserStatsRepository repository) {
        this.repository = repository;
    }

    public UserStats getOrEmpty(ObjectId userId) {
        return repository.findByUserId(userId).orElse(null);
    }

    /**
     * Denormalized rollup, called when a submission first reaches a terminal status.
     * The caller must guard against double-applying — this method does no idempotency check.
     */
    public void recordTerminal(ObjectId userId, int languageId, String terminalStatus, int timeMs) {
        UserStats stats = repository.findByUserId(userId).orElseGet(() -> {
            UserStats s = new UserStats();
            s.setUserId(userId);
            s.setByLanguage(new HashMap<>());
            return s;
        });

        boolean accepted = SubmissionStatus.ACCEPTED.equals(terminalStatus);

        stats.setTotalSubmissions(stats.getTotalSubmissions() + 1);
        if (accepted) {
            stats.setAcceptedSubmissions(stats.getAcceptedSubmissions() + 1);
        }
        stats.setLastSubmissionAt(Instant.now());

        String key = String.valueOf(languageId);
        LanguageStat ls = stats.getByLanguage().get(key);
        if (ls == null) {
            ls = new LanguageStat(0, 0, 0);
        }

        long newCount = ls.getCount() + 1;
        long newAccepted = ls.getAccepted() + (accepted ? 1 : 0);
        // Running average: (oldAvg * oldCount + newSample) / newCount
        long newAvg = newCount == 0 ? 0
            : (ls.getAvgTimeMs() * ls.getCount() + Math.max(0, timeMs)) / newCount;
        stats.getByLanguage().put(key, new LanguageStat(newCount, newAccepted, newAvg));

        repository.save(stats);
        log.debug("Updated user_stats for user={} language={} terminal={}", userId, languageId, terminalStatus);
    }
}
