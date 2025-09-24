package com.example.tests.generator.config;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Immutable configuration for communicating with the Gigachat API.
 */
public class GigachatClientConfig {

    private final URI baseUrl;
    private final URI authUrl;
    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private final String model;
    private final RetryPolicy retryPolicy;
    private final Duration connectTimeout;
    private final String certificateFile;
    private final String keyFile;
    private final String caFile;
    private final String trustStorePassword;

    private GigachatClientConfig(Builder builder) {
        this.baseUrl = Objects.requireNonNull(builder.baseUrl, "baseUrl");
        this.authUrl = Objects.requireNonNull(builder.authUrl, "authUrl");
        this.clientId = Objects.requireNonNull(builder.clientId, "clientId");
        this.clientSecret = builder.clientSecret;
        this.scope = builder.scope == null ? "GIGACHAT_API_PERS" : builder.scope;
        this.model = builder.model == null ? "GigaChat" : builder.model;
        this.retryPolicy = builder.retryPolicy == null
                ? new RetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(30), 2.0)
                : builder.retryPolicy;
        this.connectTimeout = builder.connectTimeout == null ? Duration.ofSeconds(10) : builder.connectTimeout;
        this.certificateFile = builder.certificateFile;
        this.keyFile = builder.keyFile;
        this.caFile = builder.caFile;
        this.trustStorePassword = builder.trustStorePassword;
    }

    public URI getBaseUrl() {
        return baseUrl;
    }

    public URI getAuthUrl() {
        return authUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getScope() {
        return scope;
    }

    public String getModel() {
        return model;
    }

    public String getCertificateFile() {
        return certificateFile;
    }

    public String getKeyFile() {
        return keyFile;
    }

    public String getCaFile() {
        return caFile;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private URI baseUrl;
        private URI authUrl;
        private String clientId;
        private String clientSecret;
        private String scope;
        private String model;
        private RetryPolicy retryPolicy;
        private Duration connectTimeout;
        private String certificateFile;
        private String keyFile;
        private String caFile;
        private String trustStorePassword;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = URI.create(baseUrl);
            return this;
        }

        public Builder baseUrl(URI baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder authUrl(String authUrl) {
            this.authUrl = URI.create(authUrl);
            return this;
        }

        public Builder authUrl(URI authUrl) {
            this.authUrl = authUrl;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder clientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
            return this;
        }

        public Builder scope(String scope) {
            this.scope = scope;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder certificateFile(String certificateFile) {
            this.certificateFile = certificateFile;
            return this;
        }

        public Builder keyFile(String keyFile) {
            this.keyFile = keyFile;
            return this;
        }

        public Builder caFile(String caFile) {
            this.caFile = caFile;
            return this;
        }

        public Builder trustStorePassword(String trustStorePassword) {
            this.trustStorePassword = trustStorePassword;
            return this;
        }

        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public GigachatClientConfig build() {
            return new GigachatClientConfig(this);
        }
    }
}
