package com.example.clientes.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ClienteInativacaoLoteRequest(
        @NotNull @Size(min = 1, max = 100) List<@NotNull Long> ids) {
}
