package com.localdelivery.controller;

import com.localdelivery.dto.*;
import com.localdelivery.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accounts;
    private final AuthenticationManager authenticator;
    private final SessionAuthenticationStrategy sessions;
    private final SecurityContextRepository contexts;

    public AuthController(AccountService accounts, AuthenticationManager authenticator,
                          SessionAuthenticationStrategy sessions, SecurityContextRepository contexts) {
        this.accounts = accounts;
        this.authenticator = authenticator;
        this.sessions = sessions;
        this.contexts = contexts;
    }

    public record CsrfResponse(String token, String headerName) {}
    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) { return new CsrfResponse(token.getToken(), token.getHeaderName()); }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthenticatedUser register(@Valid @RequestBody RegisterRequest input) {
        return accounts.register(input);
    }

    @PostMapping("/login")
    public AuthenticatedUser login(@Valid @RequestBody LoginRequest input,
                                   HttpServletRequest request, HttpServletResponse response) {
        var authentication = authenticator.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(input.email(), input.password()));
        sessions.onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return accounts.get(Long.valueOf(authentication.getName()));
    }

    @GetMapping("/me")
    public AuthenticatedUser me(Authentication authentication) {
        return accounts.get(Long.valueOf(authentication.getName()));
    }
}
