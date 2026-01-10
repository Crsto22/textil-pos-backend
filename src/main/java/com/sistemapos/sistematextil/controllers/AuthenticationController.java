package com.sistemapos.sistematextil.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.services.AuthenticationService;
import com.sistemapos.sistematextil.util.AuthenticationRequest;
import com.sistemapos.sistematextil.util.AuthenticationResponse;
import com.sistemapos.sistematextil.util.RegisterRequest;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("api/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    @PostMapping("/registro")
    public void registrar(@RequestBody RegisterRequest request) {
        authenticationService.register(request);
    }

    @PostMapping("/autenticarse")
    public ResponseEntity <AuthenticationResponse> authenticar (@RequestBody AuthenticationRequest request ) {
        return ResponseEntity.ok(authenticationService.authenticate(request));
    }

}
