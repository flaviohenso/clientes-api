package com.example.clientes.dto;

import com.example.clientes.domain.Cliente;

import java.time.Instant;

public record ClienteResponse(Long id, String nome, String email, boolean ativo, Instant criadoEm) {

    public static ClienteResponse from(Cliente c) {
        return new ClienteResponse(c.getId(), c.getNome(), c.getEmail(), c.isAtivo(), c.getCriadoEm());
    }
}
