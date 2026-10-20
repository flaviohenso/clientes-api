package com.example.clientes.exception;

public class EmailJaCadastradoException extends RuntimeException {

    public EmailJaCadastradoException(String email) {
        super("E-mail %s já cadastrado".formatted(email));
    }
}
