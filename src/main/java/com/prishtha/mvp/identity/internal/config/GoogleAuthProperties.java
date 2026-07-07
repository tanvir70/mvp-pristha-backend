package com.prishtha.mvp.identity.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity.auth.google")
public record GoogleAuthProperties(String clientId) {
}
