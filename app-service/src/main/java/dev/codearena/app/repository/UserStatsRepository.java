package dev.codearena.app.repository;

import dev.codearena.app.domain.UserStats;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserStatsRepository extends MongoRepository<UserStats, ObjectId> {
    Optional<UserStats> findByUserId(ObjectId userId);
}
