package com.example.clientes.controller;

import com.example.clientes.dto.ClienteContagemResponse;
import com.example.clientes.dto.ClienteEmailExisteResponse;
import com.example.clientes.dto.ClienteRequest;
import com.example.clientes.dto.ClienteResponse;
import com.example.clientes.service.ClienteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@Validated
@RestController
@RequestMapping("/api/v1/clientes")
public class ClienteController {

    private final ClienteService service;

    public ClienteController(ClienteService service) {
        this.service = service;
    }

    @GetMapping
    public Page<ClienteResponse> listar(@RequestParam(required = false) String nome,
                                        @RequestParam(required = false) @Email(message = "Formato de e-mail inválido") String email,
                                        @RequestParam(required = false) Boolean ativo,
                                        @PageableDefault(size = 20, sort = "nome") Pageable pageable) {
        return service.listar(nome, email, ativo, pageable);
    }

    @GetMapping("/contagem")
    public ClienteContagemResponse contar(@RequestParam(required = false) Boolean ativo) {
        return service.contar(ativo);
    }

    @GetMapping("/email-existe")
    public ClienteEmailExisteResponse emailExiste(
            @RequestParam @NotBlank(message = "O e-mail não pode ser vazio ou em branco") String email) {
        return service.emailExiste(email);
    }

    @GetMapping("/{id}")
    public ClienteResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    public ResponseEntity<ClienteResponse> criar(@Valid @RequestBody ClienteRequest request) {
        ClienteResponse criado = service.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(criado.id());
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{id}")
    public ClienteResponse atualizar(@PathVariable Long id, @Valid @RequestBody ClienteRequest request) {
        return service.atualizar(id, request);
    }

    @PatchMapping("/{id}/reativar")
    public ClienteResponse reativar(@PathVariable Long id) {
        return service.reativar(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inativar(@PathVariable Long id) {
        service.inativar(id);
        return ResponseEntity.noContent().build();
    }
}
