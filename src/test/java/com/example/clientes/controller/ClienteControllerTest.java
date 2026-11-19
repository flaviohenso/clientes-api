package com.example.clientes.controller;

import com.example.clientes.dto.ClienteContagemResponse;
import com.example.clientes.dto.ClienteEmailExisteResponse;
import com.example.clientes.dto.ClienteEmailRequest;
import com.example.clientes.dto.ClienteRequest;
import com.example.clientes.dto.ClienteResponse;
import com.example.clientes.exception.ClienteNaoEncontradoException;
import com.example.clientes.exception.EmailJaCadastradoException;
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
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    private static String payloadEmail(String email) {
        return "{\"email\":\"%s\"}".formatted(email);
    }

    private static String emailValidoComTamanho(int tamanhoTotal) {
        String prefixo = "a".repeat(50) + "@" + "b".repeat(50) + ".";
        String sufixo = ".com";
        int restante = tamanhoTotal - prefixo.length() - sufixo.length();
        return prefixo + "c".repeat(restante) + sufixo;
    }

    private ResultActions executarPatchEmail(Long id, String payloadJson) throws Exception {
        return mvc.perform(patch("/api/v1/clientes/{id}/email", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payloadJson));
    }

    private void executarPatchEmailEsperando400(String payloadJson) throws Exception {
        executarPatchEmail(1L, payloadJson)
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

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

    @Test
    @DisplayName("Deve retornar contagem total de clientes sem filtro retornando HTTP 200 (AC-1)")
    void contarSemFiltroRetorna200ComTotal() throws Exception {
        when(service.contar(isNull())).thenReturn(new ClienteContagemResponse(8L));

        mvc.perform(get("/api/v1/clientes/contagem"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(8));
    }

    @Test
    @DisplayName("Deve retornar contagem de clientes ativos com ativo=true retornando HTTP 200 (AC-2)")
    void contarComFiltroAtivoTrueRetorna200ComTotal() throws Exception {
        when(service.contar(eq(true))).thenReturn(new ClienteContagemResponse(5L));

        mvc.perform(get("/api/v1/clientes/contagem").param("ativo", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5));
    }

    @Test
    @DisplayName("Deve retornar contagem de clientes inativos com ativo=false retornando HTTP 200 (AC-3)")
    void contarComFiltroAtivoFalseRetorna200ComTotal() throws Exception {
        when(service.contar(eq(false))).thenReturn(new ClienteContagemResponse(3L));

        mvc.perform(get("/api/v1/clientes/contagem").param("ativo", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    @DisplayName("Deve retornar contagem zerada quando não houver clientes retornando HTTP 200 (AC-4)")
    void contarBaseVaziaRetorna200ComTotalZero() throws Exception {
        when(service.contar(isNull())).thenReturn(new ClienteContagemResponse(0L));

        mvc.perform(get("/api/v1/clientes/contagem"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("Deve retornar HTTP 400 Bad Request quando parâmetro ativo for inválido (AC-5)")
    void contarComParametroAtivoInvalidoRetorna400() throws Exception {
        mvc.perform(get("/api/v1/clientes/contagem").param("ativo", "invalido"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Deve retornar 200 com existe=true quando e-mail cadastrado (AC-1)")
    void emailExisteRetorna200ComTrue() throws Exception {
        when(service.emailExiste("maria@exemplo.com")).thenReturn(new ClienteEmailExisteResponse(true));

        mvc.perform(get("/api/v1/clientes/email-existe").param("email", "maria@exemplo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existe").value(true));
    }

    @Test
    @DisplayName("Deve retornar 200 com existe=false quando e-mail não cadastrado (AC-2)")
    void emailExisteRetorna200ComFalse() throws Exception {
        when(service.emailExiste("naoexiste@exemplo.com")).thenReturn(new ClienteEmailExisteResponse(false));

        mvc.perform(get("/api/v1/clientes/email-existe").param("email", "naoexiste@exemplo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existe").value(false));
    }

    @Test
    @DisplayName("Deve repassar ao service o e-mail com maiúsculas/minúsculas misturadas (AC-3)")
    void emailExisteRepassaEmailComCaixaMistaAoService() throws Exception {
        when(service.emailExiste("MARIA@Exemplo.COM")).thenReturn(new ClienteEmailExisteResponse(true));

        mvc.perform(get("/api/v1/clientes/email-existe").param("email", "MARIA@Exemplo.COM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existe").value(true));

        verify(service).emailExiste("MARIA@Exemplo.COM");
    }

    @Test
    @DisplayName("Deve retornar 400 ProblemDetail quando o parâmetro email não for informado (AC-4)")
    void emailExisteSemParametroRetorna400ProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/clientes/email-existe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Deve retornar 400 ProblemDetail quando email for vazio (AC-4)")
    void emailExisteComEmailVazioRetorna400ProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/clientes/email-existe").param("email", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Deve retornar 400 ProblemDetail quando email estiver em branco (AC-4)")
    void emailExisteComEmailEmBrancoRetorna400ProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/clientes/email-existe").param("email", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Deve atualizar e-mail e retornar 200 OK preservando nome, ativo e criadoEm (CLI-28 AC-1)")
    void atualizarEmailRetorna200PreservandoDemaisCampos() throws Exception {
        Instant criadoEm = Instant.parse("2026-01-10T10:00:00Z");
        ClienteResponse response = new ClienteResponse(1L, "Maria Silva", "maria.nova@exemplo.com", true, criadoEm);
        when(service.atualizarEmail(eq(1L), any(ClienteEmailRequest.class))).thenReturn(response);

        executarPatchEmail(1L, payloadEmail("maria.nova@exemplo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Maria Silva"))
                .andExpect(jsonPath("$.email").value("maria.nova@exemplo.com"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.criadoEm").value("2026-01-10T10:00:00Z"));

        verify(service).atualizarEmail(eq(1L), eq(new ClienteEmailRequest("maria.nova@exemplo.com")));
    }

    @Test
    @DisplayName("Deve remover espaços laterais do e-mail e retornar 200 OK (CLI-28 AC-2)")
    void atualizarEmailComEspacosLateraisFazTrimERetorna200() throws Exception {
        Instant criadoEm = Instant.parse("2026-01-10T10:00:00Z");
        ClienteResponse response = new ClienteResponse(1L, "Maria Silva", "nova.com.espacos@exemplo.com", true, criadoEm);
        when(service.atualizarEmail(eq(1L), any(ClienteEmailRequest.class))).thenReturn(response);

        executarPatchEmail(1L, payloadEmail("  nova.com.espacos@exemplo.com  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Maria Silva"))
                .andExpect(jsonPath("$.email").value("nova.com.espacos@exemplo.com"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.criadoEm").value("2026-01-10T10:00:00Z"));

        verify(service).atualizarEmail(eq(1L), eq(new ClienteEmailRequest("nova.com.espacos@exemplo.com")));
    }

    @Test
    @DisplayName("Deve tratar atualização com mesmo e-mail case-insensitive de forma idempotente retornando 200 OK (CLI-28 AC-3)")
    void atualizarEmailIdempotenteCaseInsensitiveRetorna200() throws Exception {
        Instant criadoEm = Instant.parse("2026-01-10T10:00:00Z");
        ClienteResponse response = new ClienteResponse(1L, "Maria Silva", "maria@exemplo.com", true, criadoEm);
        when(service.atualizarEmail(eq(1L), any(ClienteEmailRequest.class))).thenReturn(response);

        executarPatchEmail(1L, payloadEmail("MARIA@EXEMPLO.COM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Maria Silva"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.criadoEm").value("2026-01-10T10:00:00Z"));

        verify(service).atualizarEmail(eq(1L), eq(new ClienteEmailRequest("MARIA@EXEMPLO.COM")));
    }

    @Test
    @DisplayName("Deve atualizar e-mail de cliente inativo mantendo ativo=false e retornando 200 OK (CLI-28 AC-4)")
    void atualizarEmailDeClienteInativoMantemAtivoFalse() throws Exception {
        Instant criadoEm = Instant.parse("2026-01-05T08:30:00Z");
        ClienteResponse response = new ClienteResponse(2L, "João Souza", "joao.atualizado@exemplo.com", false, criadoEm);
        when(service.atualizarEmail(eq(2L), any(ClienteEmailRequest.class))).thenReturn(response);

        executarPatchEmail(2L, payloadEmail("joao.atualizado@exemplo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.nome").value("João Souza"))
                .andExpect(jsonPath("$.email").value("joao.atualizado@exemplo.com"))
                .andExpect(jsonPath("$.ativo").value(false))
                .andExpect(jsonPath("$.criadoEm").value("2026-01-05T08:30:00Z"));

        verify(service).atualizarEmail(eq(2L), eq(new ClienteEmailRequest("joao.atualizado@exemplo.com")));
    }

    @Test
    @DisplayName("Deve retornar 409 Conflict ProblemDetail quando e-mail já estiver cadastrado para outro cliente (CLI-28 AC-5)")
    void atualizarEmailDuplicadoRetorna409ProblemDetail() throws Exception {
        when(service.atualizarEmail(eq(1L), any(ClienteEmailRequest.class)))
                .thenThrow(EmailJaCadastradoException.class);

        executarPatchEmail(1L, payloadEmail("outro.cliente@exemplo.com"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").isNotEmpty());
    }

    @Test
    @DisplayName("Deve retornar 404 Not Found ProblemDetail quando id não existir (CLI-28 AC-6)")
    void atualizarEmailDeClienteInexistenteRetorna404ProblemDetail() throws Exception {
        when(service.atualizarEmail(eq(999L), any(ClienteEmailRequest.class)))
                .thenThrow(new ClienteNaoEncontradoException(999L));

        executarPatchEmail(999L, payloadEmail("qualquer@exemplo.com"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Cliente 999 não encontrado"));
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request quando campo email estiver ausente no payload (CLI-28 AC-7)")
    void atualizarEmailSemCampoEmailRetorna400() throws Exception {
        executarPatchEmailEsperando400("{}");
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request quando campo email for nulo (CLI-28 AC-7)")
    void atualizarEmailComValorNuloRetorna400() throws Exception {
        executarPatchEmailEsperando400("{\"email\":null}");
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request quando campo email for vazio (CLI-28 AC-7)")
    void atualizarEmailComValorVazioRetorna400() throws Exception {
        executarPatchEmailEsperando400(payloadEmail(""));
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request quando campo email contiver apenas espaços (CLI-28 AC-7)")
    void atualizarEmailComSomenteEspacosRetorna400() throws Exception {
        executarPatchEmailEsperando400(payloadEmail("   "));
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request quando e-mail possuir formato inválido (CLI-28 AC-8)")
    void atualizarEmailComFormatoInvalidoRetorna400() throws Exception {
        executarPatchEmailEsperando400(payloadEmail("email-invalido"));
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request quando e-mail válido ultrapassar 160 caracteres (CLI-28 AC-9)")
    void atualizarEmailCom161CaracteresRetorna400() throws Exception {
        executarPatchEmailEsperando400(payloadEmail(emailValidoComTamanho(161)));
    }

    @Test
    @DisplayName("Deve aceitar e-mail válido com exatos 160 caracteres retornando 200 OK (CLI-28 AC-10)")
    void atualizarEmailValidoCom160CaracteresRetorna200() throws Exception {
        String email160 = emailValidoComTamanho(160);
        Instant criadoEm = Instant.parse("2026-01-10T10:00:00Z");
        ClienteResponse response = new ClienteResponse(1L, "Maria Silva", email160, true, criadoEm);
        when(service.atualizarEmail(eq(1L), any(ClienteEmailRequest.class))).thenReturn(response);

        executarPatchEmail(1L, payloadEmail(email160))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Maria Silva"))
                .andExpect(jsonPath("$.email").value(email160))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.criadoEm").value("2026-01-10T10:00:00Z"));

        verify(service).atualizarEmail(eq(1L), eq(new ClienteEmailRequest(email160)));
    }
}
