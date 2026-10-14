package com.example.musicplayer.api;

import com.example.musicplayer.model.UserAccount;
import com.example.musicplayer.repository.UserAccountRepository;
import com.example.musicplayer.service.TokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;

    public AuthController(UserAccountRepository users, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    public record RegisterRequest(@NotBlank @Email @Size(max = 320) String email,
                                  @NotBlank @Size(min = 8, max = 72) String password) { }
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) { }

    @PostMapping("/register")
    @Transactional
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        return tokens.createToken(users.save(user));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        UserAccount user = users.findByEmailIgnoreCase(request.email().trim())
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect"));
        return tokens.createToken(user);
    }
}
