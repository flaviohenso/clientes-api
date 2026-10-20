package com.example.clientes.service;

import com.example.clientes.domain.Cliente;
import com.example.clientes.dto.ClienteRequest;
import com.example.clientes.dto.ClienteResponse;
import com.example.clientes.exception.ClienteNaoEncontradoException;
import com.example.clientes.exception.EmailJaCadastradoException;
import com.example.clientes.repository.ClienteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ClienteService {

    private final ClienteRepository repository;

    public ClienteService(ClienteRepository repository) {
        this.repository = repository;
    }

    public Page<ClienteResponse> listar(String nome, Pageable pageable) {
        return listar(nome, null, null, pageable);
    }

    public Page<ClienteResponse> listar(String nome, String email, Pageable pageable) {
        return listar(nome, email, null, pageable);
    }

    public Page<ClienteResponse> listar(String nome, String email, Boolean ativo, Pageable pageable) {
        Page<Cliente> page;
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasNome = nome != null && !nome.isBlank();

        if (ativo != null) {
            if (hasEmail) {
                page = repository.findByEmailIgnoreCaseAndAtivo(email.trim(), ativo, pageable);
            } else if (hasNome) {
                page = repository.findByNomeContainingIgnoreCaseAndAtivo(nome, ativo, pageable);
            } else {
                page = repository.findByAtivo(ativo, pageable);
            }
        } else {
            if (hasEmail) {
                page = repository.findByEmailIgnoreCase(email.trim(), pageable);
            } else if (hasNome) {
                page = repository.findByNomeContainingIgnoreCase(nome, pageable);
            } else {
                page = repository.findAll(pageable);
            }
        }
        return page.map(ClienteResponse::from);
    }

    public ClienteResponse buscar(Long id) {
        return ClienteResponse.from(obter(id));
    }

    @Transactional
    public ClienteResponse criar(ClienteRequest request) {
        if (repository.existsByEmailIgnoreCase(request.email())) {
            throw new EmailJaCadastradoException(request.email());
        }
        return ClienteResponse.from(repository.save(new Cliente(request.nome(), request.email())));
    }

    @Transactional
    public ClienteResponse atualizar(Long id, ClienteRequest request) {
        Cliente cliente = obter(id);
        if (!cliente.getEmail().equalsIgnoreCase(request.email())
                && repository.existsByEmailIgnoreCase(request.email())) {
            throw new EmailJaCadastradoException(request.email());
        }
        cliente.atualizar(request.nome(), request.email());
        return ClienteResponse.from(cliente);
    }

    @Transactional
    public void inativar(Long id) {
        obter(id).inativar();
    }

    @Transactional
    public ClienteResponse reativar(Long id) {
        Cliente cliente = obter(id);
        cliente.reativar();
        return ClienteResponse.from(cliente);
    }

    private Cliente obter(Long id) {
        return repository.findById(id).orElseThrow(() -> new ClienteNaoEncontradoException(id));
    }
}
