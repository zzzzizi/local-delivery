package com.localdelivery.controller;

import com.localdelivery.dto.*;
import com.localdelivery.service.AccountService;
import com.localdelivery.service.AccountMailService;
import com.localdelivery.service.AccountTokenService;
import com.localdelivery.service.AuthRateLimiter;
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
    private final AccountTokenService tokens;
    private final AccountMailService mail;
    private final AuthRateLimiter limits;
    private final AuthenticationManager authenticator;
    private final SessionAuthenticationStrategy sessions;
    private final SecurityContextRepository contexts;

    public AuthController(AccountService accounts, AuthenticationManager authenticator,
                          SessionAuthenticationStrategy sessions, SecurityContextRepository contexts,
                          AccountTokenService tokens, AccountMailService mail, AuthRateLimiter limits) {
        this.accounts = accounts;
        this.tokens = tokens; this.mail = mail; this.limits = limits;
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
        limits.register();
        return accounts.register(input);
    }

    @PostMapping("/login")
    public AuthenticatedUser login(@Valid @RequestBody LoginRequest input,
                                   HttpServletRequest request, HttpServletResponse response) {
        limits.login(input.email());
        var authentication = authenticator.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(input.email(), input.password()));
        sessions.onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return accounts.get(Long.valueOf(authentication.getName()));
    }

    public record AccountMessage(String message) {}

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AccountMessage forgotPassword(@Valid @RequestBody EmailRequest input) {
        limits.mail(input.email());
        mail.request(input.email(), AccountTokenService.Purpose.RESET_PASSWORD);
        return new AccountMessage("If this email belongs to a verified account, a password reset link will arrive shortly.");
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AccountMessage resendVerification(@Valid @RequestBody EmailRequest input) {
        limits.mail(input.email());
        mail.request(input.email(), AccountTokenService.Purpose.VERIFY_EMAIL);
        return new AccountMessage("If this account needs verification, a new link will arrive shortly.");
    }

    @PostMapping("/verify-email")
    public AccountMessage verifyEmail(@Valid @RequestBody TokenRequest input) {
        limits.token();
        tokens.verify(input.token());
        return new AccountMessage("Email verified. You can now log in.");
    }

    @PostMapping("/reset-password")
    public AccountMessage resetPassword(@Valid @RequestBody ResetPasswordRequest input) {
        limits.token();
        String email = tokens.reset(input.token(), input.password());
        mail.passwordChanged(email);
        return new AccountMessage("Password updated. Log in with your new password. Previous sessions have been signed out.");
    }

    @GetMapping("/me")
    public AuthenticatedUser me(Authentication authentication) {
        return accounts.get(Long.valueOf(authentication.getName()));
    }
}
