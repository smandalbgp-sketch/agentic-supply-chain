package com.example.agentic.persistence;

import com.example.agentic.model.DecisionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface DecisionRepository extends JpaRepository<DecisionEntity, String> {

    boolean existsByEventId(String eventId);

    List<DecisionEntity> findAllByOrderByCreatedAtDesc();

    List<DecisionEntity> findByStatusOrderByCreatedAtDesc(DecisionStatus status);

    /**
     * Compare-and-set in ONE SQL statement: only a row that is still in {@code from} is updated.
     * Returns the number of rows changed (1 = we won, 0 = missing or someone else already decided).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DecisionEntity d set d.status = :target, d.updatedAt = :now "
            + "where d.decisionId = :id and d.status = :from")
    int transition(@Param("id") String id,
                   @Param("from") DecisionStatus from,
                   @Param("target") DecisionStatus target,
                   @Param("now") Instant now);
}
