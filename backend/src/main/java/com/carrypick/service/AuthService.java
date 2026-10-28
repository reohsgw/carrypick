package com.carrypick.service;

import com.carrypick.common.ApiException;
import com.carrypick.config.JwtService;
import com.carrypick.domain.Enums;
import com.carrypick.domain.User;
import com.carrypick.repo.UserRepository;
import com.carrypick.web.dto.Dtos.AuthResponse;
import com.carrypick.web.dto.Dtos.UserDto;
import com.carrypick.web.dto.Forms.LoginForm;
import com.carrypick.web.dto.Forms.SignupForm;
import com.carrypick.web.dto.Forms.UpdateMeForm;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public AuthResponse signup(SignupForm f) {
        if (users.existsByEmailIgnoreCase(f.email())) {
            throw ApiException.conflict("EMAIL_TAKEN", "This email is already registered");
        }
        Enums.Mode mode = f.mode() == null ? Enums.Mode.BUYER : f.mode();
        User u = users.save(new User(f.email().trim().toLowerCase(), encoder.encode(f.password()),
                f.name().trim(), mode, false));
        return new AuthResponse(jwt.issue(u.getId(), u.getEmail()), UserDto.of(u));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginForm f) {
        User u = users.findByEmailIgnoreCase(f.email().trim())
                .filter(x -> encoder.matches(f.password(), x.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS",
                        "Email or password is incorrect"));
        return new AuthResponse(jwt.issue(u.getId(), u.getEmail()), UserDto.of(u));
    }

    @Transactional(readOnly = true)
    public User require(Long userId) {
        return users.findById(userId).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Please log in again"));
    }

    @Transactional
    public UserDto updateMe(Long userId, UpdateMeForm f) {
        User u = require(userId);
        if (f.name() != null && !f.name().isBlank()) u.setName(f.name().trim());
        if (f.mode() != null) u.setCurrentMode(f.mode());
        return UserDto.of(u);
    }
}
