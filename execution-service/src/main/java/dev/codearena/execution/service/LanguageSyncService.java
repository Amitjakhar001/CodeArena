package dev.codearena.execution.service;

import dev.codearena.execution.domain.ExecutionLanguage;
import dev.codearena.execution.dto.Judge0Language;
import dev.codearena.execution.repository.ExecutionLanguageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class LanguageSyncService {

    private static final Logger log = LoggerFactory.getLogger(LanguageSyncService.class);

    private final Judge0Client judge0;
    private final ExecutionLanguageRepository repo;

    public LanguageSyncService(Judge0Client judge0, ExecutionLanguageRepository repo) {
        this.judge0 = judge0;
        this.repo = repo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        try {
            sync();
        } catch (RuntimeException e) {
            // Don't crash boot if Judge0 is briefly unavailable — init.sql seed is enough.
            log.warn("Initial language sync failed; will retry on schedule. cause={}", e.getMessage());
        }
    }

    @Scheduled(cron = "${judge0.language-sync-cron:0 0 3 * * *}")
    public void syncOnSchedule() {
        sync();
    }

    @Transactional
    public void sync() {
        List<Judge0Language> remote = judge0.listLanguages();
        if (remote.isEmpty()) {
            log.warn("Judge0 returned no languages — skipping sync");
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        Set<Integer> seen = new HashSet<>();
        for (Judge0Language l : remote) {
            seen.add(l.id());
            ExecutionLanguage row = repo.findById(l.id()).orElseGet(ExecutionLanguage::new);
            row.setId(l.id());
            row.setName(l.name());
            row.setActive(true);
            row.setCachedAt(now);
            repo.save(row);
        }
        // Languages no longer in Judge0 → mark inactive (preserves FK integrity).
        for (ExecutionLanguage existing : repo.findAll()) {
            if (!seen.contains(existing.getId()) && existing.isActive()) {
                existing.setActive(false);
                existing.setCachedAt(now);
                repo.save(existing);
            }
        }
        log.info("Language sync: {} languages cached", remote.size());
    }
}
