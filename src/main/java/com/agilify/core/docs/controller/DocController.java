package com.agilify.core.docs.controller;

import com.agilify.core.common.security.CurrentUser;
import com.agilify.core.docs.dto.CreateDocRequest;
import com.agilify.core.docs.dto.DocResponse;
import com.agilify.core.docs.dto.UpdateDocTitleRequest;
import com.agilify.core.docs.service.DocService;
import com.agilify.core.docs.service.DocSnapshotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Docs", description = "Collaborative document metadata; live content is over /ws/docs/{docId}")
@RestController
@RequestMapping("/api/v1/projects/{key}/docs")
@RequiredArgsConstructor
public class DocController {

    private final DocService docService;
    private final DocSnapshotService snapshotService;

    @Operation(summary = "Create a doc", description = "Requires DEVELOPER role.")
    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'DEVELOPER')")
    @PostMapping
    public ResponseEntity<DocResponse> create(
            @PathVariable String key, @Valid @RequestBody(required = false) CreateDocRequest request,
            @CurrentUser UUID userId) {
        CreateDocRequest body = request != null ? request : new CreateDocRequest(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(docService.create(key, body, userId));
    }

    @Operation(summary = "List project docs", description = "Requires project membership.")
    @PreAuthorize("@projectSecurity.isMember(#key, #userId.toString())")
    @GetMapping
    public ResponseEntity<List<DocResponse>> list(@PathVariable String key, @CurrentUser UUID userId) {
        return ResponseEntity.ok(docService.list(key));
    }

    @Operation(summary = "Rename a doc", description = "Requires DEVELOPER role.")
    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'DEVELOPER')")
    @PatchMapping("/{docId}/title")
    public ResponseEntity<DocResponse> updateTitle(
            @PathVariable String key, @PathVariable UUID docId,
            @Valid @RequestBody UpdateDocTitleRequest request, @CurrentUser UUID userId) {
        return ResponseEntity.ok(docService.updateTitle(key, docId, request));
    }

    @Operation(summary = "Delete a doc", description = "Soft delete. Requires DEVELOPER role.")
    @PreAuthorize("@projectSecurity.hasRole(#key, #userId.toString(), 'DEVELOPER')")
    @DeleteMapping("/{docId}")
    public ResponseEntity<Void> delete(@PathVariable String key, @PathVariable UUID docId, @CurrentUser UUID userId) {
        docService.delete(key, docId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Compact the update log", description = "Client sends its fully-merged Yjs state to collapse doc_snapshots to one row. Requires project membership.")
    @PreAuthorize("@projectSecurity.isMember(#key, #userId.toString())")
    @PostMapping(value = "/{docId}/compact", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Void> compact(
            @PathVariable String key, @PathVariable UUID docId,
            @RequestBody byte[] fullState, @CurrentUser UUID userId) {
        snapshotService.compact(docId, fullState);
        return ResponseEntity.ok().build();
    }
}
