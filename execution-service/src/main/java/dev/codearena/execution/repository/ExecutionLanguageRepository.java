package dev.codearena.execution.repository;

import dev.codearena.execution.domain.ExecutionLanguage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionLanguageRepository extends JpaRepository<ExecutionLanguage, Integer> {
}
