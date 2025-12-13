package com.example.fintrack.controller;

import com.example.fintrack.dto.AuthResponse;
import com.example.fintrack.dto.LoginRequest;
import com.example.fintrack.dto.RegisterRequest;
import com.example.fintrack.model.Role;
import com.example.fintrack.model.User;
import com.example.fintrack.repository.UserRepository;
import com.example.fintrack.security.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwt;

    public AuthController(UserRepository userRepo, PasswordEncoder encoder, JwtUtil jwt) {
        this.userRepo = userRepo;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    // Small DTO to return user info (used in /register and /me)
    public record UserView(Long id, String email, String role) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        if (userRepo.existsByEmail(email)) {
            return ResponseEntity.status(CONFLICT).body(Map.of(
                    "code", "EMAIL_TAKEN",
                    "message", "An account with this email already exists."
            ));
        }

        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(req.getPassword()));
        if (u.getRole() == null) u.setRole(Role.USER);
        userRepo.save(u);

        // Do NOT auto-login. Return 201 + created user; frontend will redirect to /login
        return ResponseEntity.status(CREATED)
                .location(URI.create("/auth/users/" + u.getId()))
                .body(new UserView(u.getId(), u.getEmail(), u.getRole().name()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        var opt = userRepo.findByEmail(email);
        if (opt.isEmpty() || !encoder.matches(req.getPassword(), opt.get().getPasswordHash())) {
            return ResponseEntity.status(UNAUTHORIZED).body(Map.of(
                    "code", "INVALID_CREDENTIALS",
                    "message", "Invalid email or password."
            ));
        }
        String token = jwt.generateToken(email);
        // Keep your existing AuthResponse shape ({ token }) so no frontend break
        return ResponseEntity.ok(new AuthResponse(token));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(org.springframework.security.core.Authentication auth) {
        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "code", "UNAUTHENTICATED",
                    "message", "You are not logged in."
            ));
        }
        var u = userRepo.findByEmail(auth.getName()).orElse(null);
        if (u == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "code", "INVALID_SESSION",
                    "message", "Your session is invalid or expired."
            ));
        }
        record UserView(Long id, String email, String role) {}
        return ResponseEntity.ok(new UserView(u.getId(), u.getEmail(), u.getRole().name()));
    }
}
