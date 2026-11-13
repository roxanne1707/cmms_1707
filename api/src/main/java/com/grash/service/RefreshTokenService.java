package com.grash.service;

import com.grash.dto.AuthTokens;
import com.grash.exception.CustomException;
import com.grash.model.RefreshToken;
import com.grash.model.User;
import com.grash.repository.RefreshTokenRepository;
import com.grash.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final com.grash.security.SessionRegistry sessionRegistry;

    @Value("${security.jwt.refresh.expire-length:604800000}")
    private long refreshTokenValidityInMilliseconds;

    @Transactional
    public AuthTokens createTokenPair(User user) {
        // New LDAP/signup users must be visible before the JDBC session lock and FK insert.
        refreshTokenRepository.flush();
        UUID sessionId = sessionRegistry.create(user.getId(), refreshExpiry());
        String accessToken = jwtTokenProvider.createToken(user.getEmail(),
                Collections.singletonList(user.getRole().getRoleType()), sessionId);
        String refreshToken = createRefreshToken(user, sessionId);
        return new AuthTokens(accessToken, refreshToken, jwtTokenProvider.computeAccessTokenExpiration());
    }

    @Transactional
    public String createRefreshToken(User user) {
        refreshTokenRepository.flush();
        return createRefreshToken(user, sessionRegistry.create(user.getId(), refreshExpiry()));
    }

    private Date refreshExpiry() { return new Date(System.currentTimeMillis() + refreshTokenValidityInMilliseconds); }

    private String createRefreshToken(User user, UUID sessionId) {
        String rawToken = UUID.randomUUID() + "." + UUID.randomUUID();
        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(hash(rawToken))
                .user(user)
                .createdAt(new Date())
                .expiresAt(refreshExpiry())
                .sessionId(sessionId)
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    // Rejection after revocation is an expected security outcome: commit the revocation.
    // Other exceptions (including token creation failures) still roll back the entire rotation.
    @Transactional(noRollbackFor = RefreshRejectedException.class)
    public AuthTokens rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 512) {
            throw new RefreshRejectedException("Invalid refresh token");
        }
        sessionRegistry.lockRefreshOwner(hash(rawToken));
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new CustomException("Invalid refresh token", HttpStatus.UNAUTHORIZED));

        if (stored.isRevoked()) {
            // Token reuse detection: a previously rotated/revoked token was presented again.
            if (stored.getSessionId() == null) revokeAllForUser(stored.getUser());
            else sessionRegistry.revoke(stored.getSessionId(), stored.getUser().getId());
            throw new RefreshRejectedException("Refresh token reuse detected. Affected session revoked; please sign in again");
        }

        if (stored.getExpiresAt().before(new Date())) {
            stored.setRevoked(true);
            refreshTokenRepository.save(stored);
            throw new RefreshRejectedException("Refresh token expired. Please sign in again");
        }

        User user = stored.getUser();
        if (!user.isEnabled()) {
            revokeAllForUser(user);
            throw new RefreshRejectedException("Account is disabled");
        }
        Date sessionInvalidatedAt = user.getSessionInvalidatedAt();
        if (sessionInvalidatedAt != null && stored.getCreatedAt().before(sessionInvalidatedAt)) {
            throw new CustomException("Session has been revoked. Please sign in again", HttpStatus.UNAUTHORIZED);
        }

        UUID sessionId = stored.getSessionId();
        if (sessionId == null) sessionId = sessionRegistry.create(user.getId(), refreshExpiry());
        else sessionRegistry.rotate(sessionId, user.getId(), refreshExpiry());
        String newRawToken = createRefreshToken(user, sessionId);
        stored.setRevoked(true);
        stored.setReplacedByTokenHash(hash(newRawToken));
        refreshTokenRepository.save(stored);

        String accessToken = jwtTokenProvider.createToken(user.getEmail(),
                Collections.singletonList(user.getRole().getRoleType()), sessionId);
        return new AuthTokens(accessToken, newRawToken, jwtTokenProvider.computeAccessTokenExpiration());
    }

    @Transactional
    public void revokeAllForUser(User user) {
        sessionRegistry.revokeAll(user.getId());
        refreshTokenRepository.revokeAllByUser(user.getId());
    }

    public static final class RefreshRejectedException extends CustomException {
        public RefreshRejectedException(String message) {
            super(message, HttpStatus.UNAUTHORIZED);
        }
    }

    String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
