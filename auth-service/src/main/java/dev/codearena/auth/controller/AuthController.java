package dev.codearena.auth.controller;

import dev.codearena.auth.dto.AccessTokenResponse;
import dev.codearena.auth.dto.AuthResponse;
import dev.codearena.auth.dto.LoginRequest;
import dev.codearena.auth.dto.LogoutRequest;
import dev.codearena.auth.dto.RefreshRequest;
import dev.codearena.auth.dto.RegisterRequest;
import dev.codearena.auth.dto.UserResponse;
import dev.codearena.auth.exception.MissingAuthHeaderException;
import dev.codearena.auth.service.AuthService;
import dev.codearena.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService auth;
    private final JwtService jwt;

    public AuthController(AuthService auth, JwtService jwt) {
        this.auth = auth;
        this.jwt = jwt;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return auth.register(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @PostMapping("/refresh")
    public AccessTokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return auth.refresh(req.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest req) {
        auth.logout(req.refreshToken());
    }

    /**
     * Returns the authenticated user. In production, the gateway validates the JWT
     * and forwards X-User-Id; in dev / standalone, we accept the bearer token directly.
     */
    @GetMapping("/me")
    public UserResponse me(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (userIdHeader != null && !userIdHeader.isBlank()) {
            return auth.me(userIdHeader);
        }
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new MissingAuthHeaderException();
        }
        Claims claims = jwt.parse(authHeader.substring(7));
        return auth.me(claims.getSubject());
    }
}
