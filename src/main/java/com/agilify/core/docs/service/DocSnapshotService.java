package com.agilify.core.docs.service;

import com.agilify.core.docs.repository.DocSnapshotRepository;
import com.agilify.core.docs.repository.ProjectDocRepository;
import com.agilify.core.domain.entities.DocSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The server never decodes the Yjs binary format, so it cannot merge updates itself.
 * Each incoming update is stored as its own row, ordered by version; a newly connecting
 * client is sent every row in order and applies them one by one — correct regardless of
 * order, because that's the whole point of a CRDT.
 *
 * ponytail: the update log grows forever without compaction. compact() lets a client hand
 * back its fully-merged state (Y.encodeStateAsUpdate) to collapse the log to one row —
 * call it periodically or on last-disconnect once this matters for a busy doc.
 */
@Service
@RequiredArgsConstructor
public class DocSnapshotService {

    private final DocSnapshotRepository snapshotRepository;
    private final ProjectDocRepository docRepository;

    @Transactional
    public void appendUpdate(UUID docId, byte[] update) {
        long nextVersion = snapshotRepository.findMaxVersionByDocId(docId).orElse(0L) + 1;
        snapshotRepository.save(DocSnapshot.builder()
                .doc(docRepository.getReferenceById(docId))
                .snapshot(update)
                .version(nextVersion)
                .build());
    }

    @Transactional(readOnly = true)
    public List<byte[]> loadAllUpdates(UUID docId) {
        return snapshotRepository.findByDocIdOrderByVersionAsc(docId).stream()
                .map(DocSnapshot::getSnapshot)
                .toList();
    }

    @Transactional
    public void compact(UUID docId, byte[] fullState) {
        snapshotRepository.deleteByDocId(docId);
        snapshotRepository.save(DocSnapshot.builder()
                .doc(docRepository.getReferenceById(docId))
                .snapshot(fullState)
                .version(1L)
                .build());
    }
}
