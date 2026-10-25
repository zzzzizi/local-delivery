package com.localdelivery.service;

import com.localdelivery.dto.AuthenticatedUser;
import com.localdelivery.dto.RegisterRequest;
import com.localdelivery.model.User;
import com.localdelivery.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final UserRepository users;
    private final PasswordEncoder passwords;

    public AccountService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Transactional
    public AuthenticatedUser register(RegisterRequest input) {
        var user = new User(input.name(), input.email());
        user.setPasswordHash(passwords.encode(input.password()));
        // The database's case-insensitive unique index also covers simultaneous sign-ups.
        return AuthenticatedUser.from(users.saveAndFlush(user));
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser get(Long id) {
        return AuthenticatedUser.from(users.findById(id)
                .orElseThrow(() -> new BadCredentialsException("Please log in again.")));
    }
}
