package com.pawcare.backend.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pawcare.backend.dto.AuthResponse;
import com.pawcare.backend.dto.LoginRequest;
import com.pawcare.backend.dto.RegisterRequest;
import com.pawcare.backend.entity.User;
import com.pawcare.backend.repository.UserRepository;
import com.pawcare.backend.security.JwtUtil;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            return ResponseEntity.status(409).body("Email already registered");
        }

        User user = new User(
                null,
                req.name(),
                req.email(),
                passwordEncoder.encode(req.password()),
                List.of(req.role()),
                Instant.now()
        );
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getRoles());
        return ResponseEntity.ok(new AuthResponse(token, user.getId(), user.getRoles()));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        var userOpt = userRepository.findByEmail(req.email());

        if (userOpt.isEmpty() || !passwordEncoder.matches(req.password(), userOpt.get().getPasswordHash())) {
            return ResponseEntity.status(401).body("Invalid email or password");
        }

        User user = userOpt.get();
        String token = jwtUtil.generateToken(user.getId(), user.getRoles());
        return ResponseEntity.ok(new AuthResponse(token, user.getId(), user.getRoles()));
    }
}