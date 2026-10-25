package com.localdelivery.dto;

import com.localdelivery.model.User;

public record AuthenticatedUser(Long id, String name, String email) {
    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(user.getId(), user.getName(), user.getEmail());
    }
}
