package dev.codearena.app.service;

import dev.codearena.app.domain.Problem;
import dev.codearena.app.exception.ProblemNotFoundException;
import dev.codearena.app.repository.ProblemRepository;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProblemService {

    private final ProblemRepository repository;

    public ProblemService(ProblemRepository repository) {
        this.repository = repository;
    }

    public List<Problem> listAll() {
        return repository.findAll();
    }

    public Problem getBySlug(String slug) {
        return repository.findBySlug(slug)
            .orElseThrow(() -> new ProblemNotFoundException("No problem with slug: " + slug));
    }

    public Optional<Problem> findById(ObjectId id) {
        return repository.findById(id);
    }
}
