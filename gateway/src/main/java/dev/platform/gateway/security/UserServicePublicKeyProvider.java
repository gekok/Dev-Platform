package dev.platform.gateway.security;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

@Component
public class UserServicePublicKeyProvider {

    private final WebClient webClient;
    private volatile RSAPublicKey cachedPublicKey;

    public UserServicePublicKeyProvider(
            @Value("${gateway.upstream.user:http://localhost:8081}") String userServiceUrl) {
        this.webClient = WebClient.builder().baseUrl(userServiceUrl).build();
    }

    public Mono<RSAPublicKey> getPublicKey() {
        return Mono.defer(() -> {
            RSAPublicKey cached = cachedPublicKey;
            if (cached != null) {
                return Mono.just(cached);
            }

            return webClient.get()
                    .uri("/api/v1/auth/public-key")
                    .retrieve()
                    .bodyToMono(PublicKeyResponse.class)
                    .switchIfEmpty(Mono.error(new IllegalStateException("User service returned no public key")))
                    .map(this::parsePublicKey)
                    .doOnNext(publicKey -> cachedPublicKey = publicKey)
                    .onErrorMap(error -> new IllegalStateException(
                            "Could not retrieve the user-service RSA256 public key", error));
        });
    }

    private RSAPublicKey parsePublicKey(PublicKeyResponse response) {
        if (response == null || !"RSA256".equals(response.algorithm())
                || response.publicKey() == null || response.publicKey().isBlank()) {
            throw new IllegalStateException("User service returned an invalid RSA256 public-key response");
        }

        String encoded = response.publicKey()
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encoded);
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception exception) {
            throw new IllegalStateException("User service returned an unreadable RSA256 public key", exception);
        }
    }

    private record PublicKeyResponse(String algorithm, String publicKey) {
    }
}
