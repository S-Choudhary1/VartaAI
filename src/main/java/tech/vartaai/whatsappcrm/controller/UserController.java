package tech.vartaai.whatsappcrm.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tech.vartaai.whatsappcrm.dto.UserCreateRequest;
import tech.vartaai.whatsappcrm.service.AuthService;

@RestController
@RequestMapping("/api/v1/users")
@CrossOrigin()
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<String> createUser(@Valid @RequestBody UserCreateRequest request) {
        authService.createUser(request);
        return ResponseEntity.ok("User created successfully");
    }
}

