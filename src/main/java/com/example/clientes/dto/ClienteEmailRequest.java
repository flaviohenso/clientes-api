package com.example.clientes.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de requisição para atualização parcial do e-mail de um cliente.
 */
public record ClienteEmailRequest(
        @NotBlank @Email @Size(max = 160) String email) {

    public ClienteEmailRequest {
        // Normaliza espaços laterais antes da execução das constraints de Bean Validation.
        email = email == null ? null : email.trim();
    }
}
