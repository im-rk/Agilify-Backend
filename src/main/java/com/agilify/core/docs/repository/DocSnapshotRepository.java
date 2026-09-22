package com.agilify.core.docs.repository;

import com.agilify.core.domain.entities.DocSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocSnapshotRepository extends JpaRepository<DocSnapshot, UUID> {

    List<DocSnapshot> findByDocIdOrderByVersionAsc(UUID docId);

    @Query("SELECT MAX(s.version) FROM DocSnapshot s WHERE s.doc.id = :docId")
    Optional<Long> findMaxVersionByDocId(@Param("docId") UUID docId);

    @Modifying
    @Query("DELETE FROM DocSnapshot s WHERE s.doc.id = :docId")
    void deleteByDocId(@Param("docId") UUID docId);
}
