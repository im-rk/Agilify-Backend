package com.agilify.core.watchers.controller;

import com.agilify.core.common.security.CurrentUser;
import com.agilify.core.watchers.dto.WatcherResponse;
import com.agilify.core.watchers.service.WatcherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Watchers", description = "Subscribe to issues to be notified of changes")
@RestController
@RequestMapping("/api/v1/projects/{key}/issues/{issueId}/watchers")
@RequiredArgsConstructor
public class WatcherController {

    private final WatcherService watcherService;

    @Operation(summary = "Watch an issue", description = "Subscribes the caller. Idempotent. Requires project membership.")
    @PreAuthorize("@projectSecurity.isMember(#key, #userId.toString())")
    @PostMapping
    public ResponseEntity<Void> watch(@PathVariable String key, @PathVariable UUID issueId, @CurrentUser UUID userId) {
        watcherService.watch(key, issueId, userId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Stop watching an issue", description = "Requires project membership.")
    @PreAuthorize("@projectSecurity.isMember(#key, #userId.toString())")
    @DeleteMapping
    public ResponseEntity<Void> unwatch(@PathVariable String key, @PathVariable UUID issueId, @CurrentUser UUID userId) {
        watcherService.unwatch(key, issueId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List watchers of an issue", description = "Requires project membership.")
    @PreAuthorize("@projectSecurity.isMember(#key, #userId.toString())")
    @GetMapping
    public ResponseEntity<List<WatcherResponse>> list(@PathVariable String key, @PathVariable UUID issueId, @CurrentUser UUID userId) {
        return ResponseEntity.ok(watcherService.list(key, issueId));
    }
}
