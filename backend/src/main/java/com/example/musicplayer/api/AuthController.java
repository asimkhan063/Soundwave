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

    public record RegisterRequest(String identifier, @Email @Size(max = 320) String email,
                                  @Size(max = 30) String phoneNumber,
                                  @NotBlank @Size(min = 8, max = 72) String password) { }
    public record LoginRequest(String identifier, @Email @Size(max = 320) String email,
                               @Size(max = 30) String phoneNumber,
                               @NotBlank String password) { }

    @PostMapping("/register")
    @Transactional
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        Contact contact = resolveContact(request.identifier(), request.email(), request.phoneNumber());
        if (contact.email() != null && users.existsByEmailIgnoreCase(contact.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        if (contact.phoneNumber() != null && users.existsByPhoneNumber(contact.phoneNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this phone number already exists");
        }
        UserAccount user = new UserAccount();
        user.setEmail(contact.email());
        user.setPhoneNumber(contact.phoneNumber());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        return tokens.createToken(users.save(user));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        Contact contact = resolveContact(request.identifier(), request.email(), request.phoneNumber());
        UserAccount user = (contact.email() != null
                ? users.findByEmailIgnoreCase(contact.email())
                : users.findByPhoneNumber(contact.phoneNumber()))
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email/phone number or password is incorrect"));
        return tokens.createToken(user);
    }

    private record Contact(String email, String phoneNumber) { }

    private Contact resolveContact(String identifier, String email, String phoneNumber) {
        String value = identifier != null && !identifier.isBlank() ? identifier.trim()
                : email != null && !email.isBlank() ? email.trim()
                : phoneNumber != null && !phoneNumber.isBlank() ? phoneNumber.trim() : null;
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email ya phone number likho");
        }
        if (value.contains("@")) {
            if (!value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid email address likho");
            }
            return new Contact(value.toLowerCase(), null);
        }
        String normalized = value.replaceAll("[\\s().-]", "");
        if (!normalized.matches("\\+?[0-9]{7,15}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid phone number likho (7-15 digits)");
        }
        return new Contact(null, normalized);
    }
}
