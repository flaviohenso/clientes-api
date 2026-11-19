package com.example.clientes.dto;

import java.util.List;

public record ClienteInativacaoLoteResponse(int totalProcessados, List<Long> idsInativados) {
}
