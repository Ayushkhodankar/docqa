package com.docqa.security;

/** The authenticated principal placed in the SecurityContext by {@link JwtAuthFilter}. */
public record AuthUser(Long id, String email) {}
