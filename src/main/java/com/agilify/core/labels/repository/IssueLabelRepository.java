package com.agilify.core.labels.repository;

import com.agilify.core.domain.entities.Label;
import com.agilify.core.domain.entities.IssueLabel;
import com.agilify.core.domain.entities.IssueLabelId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface IssueLabelRepository extends JpaRepository<IssueLabel, IssueLabelId> {

    @Query("SELECT l FROM IssueLabel il JOIN il.label l WHERE il.id.issueId = :issueId ORDER BY l.name")
    List<Label> findLabelsByIssueId(@Param("issueId") UUID issueId);

    @Modifying
    @Query("DELETE FROM IssueLabel il WHERE il.id.labelId = :labelId")
    void deleteAllByLabelId(@Param("labelId") UUID labelId);
}
