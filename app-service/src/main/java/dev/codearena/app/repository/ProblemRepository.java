package dev.codearena.app.repository;

import dev.codearena.app.domain.Problem;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ProblemRepository extends MongoRepository<Problem, ObjectId> {
    Optional<Problem> findBySlug(String slug);
}
