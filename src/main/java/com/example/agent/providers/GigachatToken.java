package com.example.agent.providers;

import java.time.Instant;

/**
 * Holder for OAuth access token information returned by Gigachat identity service.
 */
public class GigachatToken {

    private final String accessToken;
    private final Instant expiresAt;

    public GigachatToken(String accessToken, Instant expiresAt) {
        this.accessToken = accessToken;
        this.expiresAt = expiresAt;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now().minusSeconds(30));
    }
}
