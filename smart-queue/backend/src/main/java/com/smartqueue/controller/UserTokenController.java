package com.smartqueue.controller;

import com.smartqueue.dto.TokenResponse;
import com.smartqueue.service.TokenService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserTokenController {

    private final TokenService tokenService;

    public UserTokenController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @GetMapping("/{userId}/tokens")
    public List<TokenResponse> getUserTokens(
            @PathVariable String userId,
            Authentication authentication) {
        return tokenService.getUserTokens(
                userId,
                authentication.getName(),
                isElevated(authentication));
    }

    private boolean isElevated(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_STAFF")
                        || authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
