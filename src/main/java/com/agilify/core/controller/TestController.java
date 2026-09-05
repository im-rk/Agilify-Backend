package com.agilify.core.controller;

import com.agilify.core.common.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/test")
public class TestController {

    @GetMapping
    public String testAuth(@CurrentUser UUID userId)
    {
        return "Auth successful! Your synced Database UUID is: " + userId;
    }
}
