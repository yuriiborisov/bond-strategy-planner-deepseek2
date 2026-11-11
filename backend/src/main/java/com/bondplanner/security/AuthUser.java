package com.bondplanner.security;

import com.bondplanner.domain.model.Role;

public record AuthUser(Long id, String email, Role role) {
}
