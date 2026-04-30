package dev.codearena.auth.service;

import dev.codearena.auth.domain.User;
import dev.codearena.auth.domain.UserRole;
import dev.codearena.auth.dto.AccessTokenResponse;
import dev.codearena.auth.dto.AuthResponse;
import dev.codearena.auth.dto.LoginRequest;
import dev.codearena.auth.dto.RegisterRequest;
import dev.codearena.auth.dto.UserResponse;
import dev.codearena.auth.exception.EmailAlreadyExistsException;
import dev.codearena.auth.exception.InvalidCredentialsException;
import dev.codearena.auth.exception.UsernameAlreadyExistsException;
import dev.codearena.auth.repository.UserRepository;
import org.bson.types.ObjectId;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final RefreshTokenService refreshTokens;
    private final JwtService jwt;
    private final PasswordEncoder encoder;

    public AuthService(UserRepository userRepo,
                       RefreshTokenService refreshTokens,
                       JwtService jwt,
                       PasswordEncoder encoder) {
        this.userRepo = userRepo;
        this.refreshTokens = refreshTokens;
        this.jwt = jwt;
        this.encoder = encoder;
    }

    public AuthResponse register(RegisterRequest req) {
        if (userRepo.existsByEmail(req.email())) {
            throw new EmailAlreadyExistsException(req.email());
        }
        if (userRepo.existsByUsername(req.username())) {
            throw new UsernameAlreadyExistsException(req.username());
        }

        User user = new User();
        user.setEmail(req.email());
        user.setUsername(req.username());
        user.setPasswordHash(encoder.encode(req.password()));
        user.setRole(UserRole.USER);
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setLastLoginAt(now);
        userRepo.save(user);

        return issueTokens(user);
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepo.findByEmail(req.emailOrUsername())
                .or(() -> userRepo.findByUsername(req.emailOrUsername()))
                .orElseThrow(InvalidCredentialsException::new);

        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        user.setLastLoginAt(Instant.now());
        userRepo.save(user);
        return issueTokens(user);
    }

    public AccessTokenResponse refresh(String refreshToken) {
        var doc = refreshTokens.validate(refreshToken);
        User user = userRepo.findById(doc.getUserId())
                .orElseThrow(InvalidCredentialsException::new);
        return new AccessTokenResponse(jwt.generateAccessToken(user));
    }

    public void logout(String refreshToken) {
        refreshTokens.revoke(refreshToken);
    }

    public UserResponse me(String userId) {
        User user = userRepo.findById(new ObjectId(userId))
                .orElseThrow(InvalidCredentialsException::new);
        return UserResponse.from(user);
    }

    private AuthResponse issueTokens(User user) {
        String access = jwt.generateAccessToken(user);
        var issued = refreshTokens.issue(user.getId());
        return new AuthResponse(UserResponse.from(user), access, issued.rawToken());
    }
}
