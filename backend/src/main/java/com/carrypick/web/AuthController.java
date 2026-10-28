package com.carrypick.web;

import com.carrypick.config.AppProperties;
import com.carrypick.config.AuthUser;
import com.carrypick.domain.Countries;
import com.carrypick.repo.NotificationRepository;
import com.carrypick.service.AuthService;
import com.carrypick.web.dto.Dtos.*;
import com.carrypick.web.dto.Forms.LoginForm;
import com.carrypick.web.dto.Forms.SignupForm;
import com.carrypick.web.dto.Forms.UpdateMeForm;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService auth;
    private final AppProperties props;
    private final NotificationRepository notifications;

    public AuthController(AuthService auth, AppProperties props, NotificationRepository notifications) {
        this.auth = auth;
        this.props = props;
        this.notifications = notifications;
    }

    /** C1 */
    @PostMapping("/auth/signup")
    public AuthResponse signup(@Valid @RequestBody SignupForm form) {
        return auth.signup(form);
    }

    /** C1 */
    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginForm form) {
        return auth.login(form);
    }

    /** C2 */
    @GetMapping("/users/me")
    public Map<String, Object> me(@AuthenticationPrincipal AuthUser me) {
        UserDto user = UserDto.of(auth.require(me.id()));
        long unread = notifications.countByUserIdAndReadFalse(me.id());
        return Map.of("user", user, "unreadNotifications", unread);
    }

    /** C2: rename or switch Buyer / Traveler mode. */
    @PatchMapping("/users/me")
    public UserDto updateMe(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UpdateMeForm form) {
        return auth.updateMe(me.id(), form);
    }

    /** Public settings the React app needs (payment mode, Toss client key, pricing rates, countries). */
    @GetMapping("/config")
    public ConfigDto config() {
        var pay = props.payment();
        var pricing = props.pricing();
        return new ConfigDto(pay.isMock() ? "mock" : "toss", pay.isMock() ? null : pay.tossClientKey(),
                pricing.feeRate(), pricing.taxThresholdKrw(), pricing.taxRate(), Countries.ALL);
    }

    @GetMapping("/countries")
    public List<Countries.Country> countries() {
        return Countries.ALL;
    }
}
