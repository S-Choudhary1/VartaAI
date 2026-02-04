package tech.vartaai.whatsappcrm.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.vartaai.whatsappcrm.dto.AuthRequest;
import tech.vartaai.whatsappcrm.dto.AuthResponse;
import tech.vartaai.whatsappcrm.service.AuthService;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin()
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody AuthRequest request) {
        authService.register(request);
        return ResponseEntity.ok("User created successfully");
    }
}
