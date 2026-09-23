package com.agilify.core.users.controller;

import com.agilify.core.auth.repository.UserRepository;
import com.agilify.core.users.dto.UserSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Users", description = "User lookup, e.g. finding someone to add to a project")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @Operation(summary = "Search users by email", description = "Any authenticated user. Used to find a user's ID before adding them to a project.")
    @GetMapping("/search")
    public ResponseEntity<List<UserSummaryResponse>> search(@RequestParam String email) {
        List<UserSummaryResponse> results = userRepository.searchByEmailAndDeletedAtIsNull(email).stream()
                .map(u -> new UserSummaryResponse(u.getId(), u.getFullName(), u.getEmail()))
                .toList();
        return ResponseEntity.ok(results);
    }
}
