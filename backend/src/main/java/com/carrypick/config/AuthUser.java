package com.carrypick.config;

/** The logged-in user, put into the security context by JwtAuthFilter. */
public record AuthUser(Long id, String email) {}
