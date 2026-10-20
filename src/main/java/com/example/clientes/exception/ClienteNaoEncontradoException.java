package com.example.clientes.exception;

public class ClienteNaoEncontradoException extends RuntimeException {

    public ClienteNaoEncontradoException(Long id) {
        super("Cliente %d não encontrado".formatted(id));
    }
}
