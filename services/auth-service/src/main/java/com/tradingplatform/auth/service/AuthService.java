package com.tradingplatform.auth.service;

import com.tradingplatform.auth.api.dto.LoginRequest;
import com.tradingplatform.auth.api.dto.RegisterRequest;
import com.tradingplatform.auth.domain.User;
import com.tradingplatform.auth.domain.UserRepository;
import com.tradingplatform.auth.token.JwtService;
import com.tradingplatform.auth.token.RefreshTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;

    public AuthService(UserRepository users,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
    }

    @Transactional
    public TokenPair register(RegisterRequest request) {
        String email = request.email().toLowerCase();
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        User user = users.save(User.create(email, passwordEncoder.encode(request.password())));
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public TokenPair login(LoginRequest request) {
        User user = users.findByEmail(request.email().toLowerCase())
                .orElseThrow(InvalidCredentialsException::new);

        // Compare even on a missing user would leak timing; here the encoder cost dominates.
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (user.getStatus() == User.Status.SUSPENDED) {
            throw new AccountSuspendedException();
        }

        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public TokenPair refresh(String presentedRefreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(presentedRefreshToken);

        User user = users.findById(rotation.userId())
                .orElseThrow(InvalidCredentialsException::new);

        return new TokenPair(
                jwtService.generateAccessToken(user),
                rotation.newRefreshToken(),
                jwtService.accessTokenTtlSeconds()
        );
    }

    public void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken);
    }

    @Transactional(readOnly = true)
    public User findById(UUID userId) {
        return users.findById(userId).orElseThrow(UserNotFoundException::new);
    }

    private TokenPair issueTokens(User user) {
        return new TokenPair(
                jwtService.generateAccessToken(user),
                refreshTokens.issue(user.getId()),
                jwtService.accessTokenTtlSeconds()
        );
    }

    public record TokenPair(String accessToken, String refreshToken, long accessTokenTtlSeconds) {
    }
}
