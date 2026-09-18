package com.smartqueue.controller;

import com.smartqueue.dto.CreateTokenRequest;
import com.smartqueue.dto.TokenResponse;
import com.smartqueue.service.TokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tokens")
public class TokenController {

    private final TokenService tokenService;

    public TokenController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping
    public ResponseEntity<TokenResponse> createToken(
            @Valid @RequestBody CreateTokenRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(tokenService.createToken(request, authentication.getName()));
    }

    @GetMapping("/{id}")
    public TokenResponse getToken(
            @PathVariable String id,
            Authentication authentication) {
        return tokenService.getToken(
                id,
                authentication.getName(),
                isElevated(authentication));
    }

    @DeleteMapping("/{id}")
    public TokenResponse cancelToken(
            @PathVariable String id,
            Authentication authentication) {
        return tokenService.cancelToken(
                id,
                authentication.getName(),
                isElevated(authentication));
    }

    private boolean isElevated(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_STAFF")
                        || authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
