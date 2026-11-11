package com.bondplanner.service.auth;

import com.bondplanner.api.dto.LoginRequest;
import com.bondplanner.api.dto.RegisterRequest;
import com.bondplanner.api.dto.TokenResponse;
import com.bondplanner.api.dto.UserDto;
import com.bondplanner.api.error.ApiException;
import com.bondplanner.config.AppProperties;
import com.bondplanner.domain.entity.RefreshToken;
import com.bondplanner.domain.entity.User;
import com.bondplanner.domain.model.Role;
import com.bondplanner.repository.RefreshTokenRepository;
import com.bondplanner.repository.UserRepository;
import com.bondplanner.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties properties;

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "CONFLICT", "Пользователь с таким email уже существует");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);
        user.setEnabled(true);
        userRepository.save(user);
        return tokens(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Неверный email или пароль"));
        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Неверный email или пароль");
        }
        return tokens(user);
    }

    @Transactional
    public TokenResponse refresh(String rawToken) {
        String hash = hash(rawToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Refresh-токен не найден"));
        if (stored.getRevokedAt() != null || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Refresh-токен истёк или отозван");
        }
        stored.setRevokedAt(Instant.now());
        refreshTokenRepository.save(stored);
        return tokens(stored.getUser());
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });
    }

    public UserDto toDto(User user) {
        return new UserDto(user.getId(), user.getEmail(), user.getRole().name());
    }

    private TokenResponse tokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = UUID.randomUUID() + "." + UUID.randomUUID();
        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(hash(refreshToken));
        entity.setExpiresAt(Instant.now().plus(properties.getJwt().getRefreshTtl()));
        entity.setCreatedAt(Instant.now());
        refreshTokenRepository.save(entity);
        long expiresIn = properties.getJwt().getAccessTtl().toSeconds();
        return new TokenResponse(accessToken, refreshToken, expiresIn, toDto(user));
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
