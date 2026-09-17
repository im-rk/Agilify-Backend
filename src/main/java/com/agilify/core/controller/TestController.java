package com.agilify.core.controller;

import com.agilify.core.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Diagnostics & Testing", description = "Test endpoints to verify authentication and database connectivity")
@RestController
@RequestMapping("/api/v1/test")
public class TestController {

    @Operation(summary = "Test authentication", description = "Validates the JWT token and returns the authenticated user's database UUID.")
    @ApiResponse(responseCode = "200", description = "Authentication successful")
    @GetMapping
    public String testAuth(@CurrentUser UUID userId)
    {
        return "Auth successful! Your synced Database UUID is: " + userId;
    }
}
