package com.example.clientes.controller;

import com.example.clientes.dto.ClienteRequest;
import com.example.clientes.dto.ClienteResponse;
import com.example.clientes.exception.ClienteNaoEncontradoException;
import com.example.clientes.service.ClienteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClienteController.class)
class ClienteControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ClienteService service;

    @Test
    void criaClienteRetorna201ComLocation() throws Exception {
        when(service.criar(any(ClienteRequest.class)))
                .thenReturn(new ClienteResponse(1L, "Ana", "ana@x.com", true, Instant.now()));

        mvc.perform(post("/api/v1/clientes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ana\",\"email\":\"ana@x.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/clientes/1"))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void emailInvalidoRetorna400() throws Exception {
        mvc.perform(post("/api/v1/clientes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Ana\",\"email\":\"invalido\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clienteInexistenteRetorna404ProblemDetail() throws Exception {
        when(service.buscar(9L)).thenThrow(new ClienteNaoEncontradoException(9L));

        mvc.perform(get("/api/v1/clientes/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Cliente 9 não encontrado"));
    }

    @Test
    @DisplayName("Deve retornar cliente correspondente ao filtrar por e-mail exato (AC-1)")
    void listarPorEmailExatoRetorna200ComCliente() throws Exception {
        ClienteResponse cliente = new ClienteResponse(1L, "Cliente Exemplo", "cliente@exemplo.com", true, Instant.now());
        when(service.listar(isNull(), eq("cliente@exemplo.com"), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cliente), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/clientes").param("email", "cliente@exemplo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("cliente@exemplo.com"));
    }

    @Test
    @DisplayName("Deve retornar cliente ao filtrar por e-mail com maiúsculas/minúsculas (AC-2)")
    void listarPorEmailCaseInsensitiveRetorna200ComCliente() throws Exception {
        ClienteResponse cliente = new ClienteResponse(1L, "Cliente Exemplo", "cliente@exemplo.com", true, Instant.now());
        when(service.listar(isNull(), eq("CLIENTE@EXEMPLO.COM"), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cliente), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/clientes").param("email", "CLIENTE@EXEMPLO.COM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("cliente@exemplo.com"));
    }

    @Test
    @DisplayName("Deve retornar lista vazia ao filtrar por e-mail inexistente (AC-3)")
    void listarPorEmailInexistenteRetorna200ComListaVazia() throws Exception {
        when(service.listar(isNull(), eq("inexistente@exemplo.com"), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get("/api/v1/clientes").param("email", "inexistente@exemplo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @DisplayName("Deve retornar 400 ProblemDetail para e-mail com formato inválido (AC-4)")
    void listarPorEmailInvalidoRetorna400ProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/clientes").param("email", "email-invalido"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Não deve retornar cliente para trecho parcial de e-mail válido (AC-5)")
    void listarPorTrechoParcialEmailValidoRetorna200Vazio() throws Exception {
        when(service.listar(isNull(), eq("cliente@exemplo.co"), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get("/api/v1/clientes").param("email", "cliente@exemplo.co"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @DisplayName("Deve reativar cliente com sucesso e retornar HTTP 200 (AC-1)")
    void reativarClienteRetorna200ComClienteReativado() throws Exception {
        ClienteResponse cliente = new ClienteResponse(123L, "Carlos", "carlos@exemplo.com", true, Instant.now());
        when(service.reativar(123L)).thenReturn(cliente);

        mvc.perform(patch("/api/v1/clientes/123/reativar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(123))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @DisplayName("Deve manter cliente ativo de forma idempotente retornando HTTP 200 (AC-2)")
    void reativarClienteJaAtivoRetorna200Idempotente() throws Exception {
        ClienteResponse cliente = new ClienteResponse(123L, "Carlos", "carlos@exemplo.com", true, Instant.now());
        when(service.reativar(123L)).thenReturn(cliente);

        mvc.perform(patch("/api/v1/clientes/123/reativar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(123))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @DisplayName("Deve retornar HTTP 404 ProblemDetail ao reativar cliente inexistente (AC-3)")
    void reativarClienteInexistenteRetorna404ProblemDetail() throws Exception {
        when(service.reativar(999L)).thenThrow(new ClienteNaoEncontradoException(999L));

        mvc.perform(patch("/api/v1/clientes/999/reativar"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Cliente 999 não encontrado"));
    }

    @Test
    @DisplayName("Deve listar clientes filtrando por ativo=true retornando HTTP 200 (AC-4)")
    void listarComFiltroAtivoTrueRetorna200() throws Exception {
        ClienteResponse cliente = new ClienteResponse(1L, "Ana", "ana@exemplo.com", true, Instant.now());
        when(service.listar(isNull(), isNull(), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cliente), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/clientes").param("ativo", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].ativo").value(true));
    }

    @Test
    @DisplayName("Deve listar clientes filtrando por ativo=false retornando HTTP 200 (AC-5)")
    void listarComFiltroAtivoFalseRetorna200() throws Exception {
        ClienteResponse cliente = new ClienteResponse(2L, "Bruno", "bruno@exemplo.com", false, Instant.now());
        when(service.listar(isNull(), isNull(), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cliente), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/v1/clientes").param("ativo", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(2))
                .andExpect(jsonPath("$.content[0].ativo").value(false));
    }

    @Test
    @DisplayName("Deve listar clientes sem informar filtro ativo mantendo comportamento legado (AC-6)")
    void listarSemFiltroAtivoRetorna200() throws Exception {
        ClienteResponse cliente1 = new ClienteResponse(1L, "Ana", "ana@exemplo.com", true, Instant.now());
        ClienteResponse cliente2 = new ClienteResponse(2L, "Bruno", "bruno@exemplo.com", false, Instant.now());
        when(service.listar(isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cliente1, cliente2), PageRequest.of(0, 20), 2));

        mvc.perform(get("/api/v1/clientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }
}
