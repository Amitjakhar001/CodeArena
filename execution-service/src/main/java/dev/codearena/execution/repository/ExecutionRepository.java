//package dev.codearena.execution.repository;
//
//import dev.codearena.execution.domain.Execution;
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Query;
//
//import java.time.OffsetDateTime;
//import java.util.List;
//import java.util.Optional;
//
//public interface ExecutionRepository extends JpaRepository<Execution, java.util.UUID> {
//
//    Optional<Execution> findByJudge0Token(String judge0Token);
//
//    List<Execution> findByJudge0TokenIn(List<String> tokens);
//
//    @Query("select e from Execution e where e.completedAt is null and e.createdAt < :before")
//    List<Execution> findStuckPending(OffsetDateTime before);
//}



package dev.codearena.execution.repository;

import dev.codearena.execution.domain.Execution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param; //  Add this import

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ExecutionRepository extends JpaRepository<Execution, java.util.UUID> {

    Optional<Execution> findByJudge0Token(String judge0Token);

    List<Execution> findByJudge0TokenIn(List<String> tokens);

    @Query("select e from Execution e where e.completedAt is null and e.createdAt < :before")
    List<Execution> findStuckPending(@Param("before") OffsetDateTime before); // <-- 2. Add @Param("before") here
}