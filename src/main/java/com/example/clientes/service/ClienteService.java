package com.example.clientes.service;

import com.example.clientes.domain.Cliente;
import com.example.clientes.dto.ClienteContagemResponse;
import com.example.clientes.dto.ClienteEmailExisteResponse;
import com.example.clientes.dto.ClienteEmailRequest;
import com.example.clientes.dto.ClienteInativacaoLoteRequest;
import com.example.clientes.dto.ClienteInativacaoLoteResponse;
import com.example.clientes.dto.ClienteRequest;
import com.example.clientes.dto.ClienteResponse;
import com.example.clientes.exception.ClienteNaoEncontradoException;
import com.example.clientes.exception.EmailJaCadastradoException;
import com.example.clientes.repository.ClienteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    public ClienteContagemResponse contar(Boolean ativo) {
        long total = ativo != null ? repository.countByAtivo(ativo) : repository.count();
        return new ClienteContagemResponse(total);
    }

    public ClienteEmailExisteResponse emailExiste(String email) {
        String emailNormalizado = email == null ? null : email.trim();
        return new ClienteEmailExisteResponse(repository.existsByEmailIgnoreCase(emailNormalizado));
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

    /**
     * Atualiza exclusivamente o e-mail de um cliente existente (ativo ou inativo),
     * validando duplicidade de forma case-insensitive apenas quando houver alteração.
     */
    @Transactional
    public ClienteResponse atualizarEmail(Long id, ClienteEmailRequest request) {
        Cliente cliente = obter(id);
        String emailNormalizado = request.email().trim();
        if (!cliente.getEmail().equalsIgnoreCase(emailNormalizado)
                && repository.existsByEmailIgnoreCase(emailNormalizado)) {
            throw new EmailJaCadastradoException(emailNormalizado);
        }
        cliente.atualizarEmail(emailNormalizado);
        return ClienteResponse.from(cliente);
    }

    @Transactional
    public void inativar(Long id) {
        obter(id).inativar();
    }

    /**
     * Inativa atomicamente e de forma idempotente os clientes cujos IDs foram informados,
     * desduplicando a lista preservando a ordem da primeira ocorrência.
     */
    @Transactional
    public ClienteInativacaoLoteResponse inativarEmLote(ClienteInativacaoLoteRequest request) {
        List<Long> idsUnicos = request.ids().stream()
                .distinct()
                .toList();

        List<Cliente> clientes = repository.findAllById(idsUnicos);
        Set<Long> idsEncontrados = clientes.stream()
                .map(Cliente::getId)
                .collect(Collectors.toSet());

        for (Long id : idsUnicos) {
            if (!idsEncontrados.contains(id)) {
                throw new ClienteNaoEncontradoException(id);
            }
        }

        clientes.forEach(Cliente::inativar);
        return new ClienteInativacaoLoteResponse(idsUnicos.size(), idsUnicos);
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
