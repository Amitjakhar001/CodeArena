package dev.codearena.app.repository;

import dev.codearena.app.domain.Submission;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SubmissionRepository extends MongoRepository<Submission, ObjectId> {
    Page<Submission> findByUserId(ObjectId userId, Pageable pageable);
    Page<Submission> findByUserIdAndProblemId(ObjectId userId, ObjectId problemId, Pageable pageable);
}
