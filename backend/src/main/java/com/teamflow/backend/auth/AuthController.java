package com.teamflow.backend.auth;

import com.teamflow.backend.auth.dto.AuthResponse;
import com.teamflow.backend.auth.dto.LoginRequest;
import com.teamflow.backend.auth.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * Completes Google sign-in. The Angular app receives the {@code code} from Google and
     * forwards it here; the response is the same token pair as a password login.
     */
    @GetMapping("/google/callback")
    public AuthResponse googleCallback(@RequestParam("code") String code) {
        return authService.loginWithGoogle(code);
    }
}
