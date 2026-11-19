package com.example.clientes;

import com.example.clientes.domain.Cliente;
import com.example.clientes.repository.ClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClienteAtualizarEmailQaIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ClienteRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    private Cliente salvarERecarregar(String nome, String email) {
        Cliente salvo = repository.save(new Cliente(nome, email));
        return repository.findById(salvo.getId()).orElseThrow();
    }

    private static String payloadEmail(String email) {
        return "{\"email\":\"%s\"}".formatted(email);
    }

    private static String emailValidoComTamanho(int tamanhoTotal) {
        String prefixo = "a".repeat(50) + "@" + "b".repeat(50) + ".";
        String sufixo = ".com";
        int restante = tamanhoTotal - prefixo.length() - sufixo.length();
        return prefixo + "c".repeat(restante) + sufixo;
    }

    private ResultActions executarPatchEmail(Object id, String payloadJson) throws Exception {
        return mvc.perform(patch("/api/v1/clientes/{id}/email", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payloadJson));
    }

    private static MvcResult assertProblemDetail(ResultActions result, int expectedStatus) throws Exception {
        return result.andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andReturn();
    }

    private static void assertSemVazamentoInterno(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("com.example"), "Resposta não deve expor pacotes internos");
        assertFalse(body.contains("org.springframework"), "Resposta não deve expor detalhes internos do framework");
        assertFalse(body.contains("java.lang"), "Resposta não deve expor classes internas Java");
        assertFalse(body.contains("stackTrace"), "Resposta não deve expor stacktrace");
        assertFalse(body.contains("SELECT "), "Resposta não deve expor instruções SQL");
    }

    @Test
    @DisplayName("Deve aceitar e-mail válido com 160 caracteres após remoção de espaços laterais (SC-6)")
    void deveAceitarEmailCom160CaracteresAposRemocaoDeEspacosLaterais() throws Exception {
        Cliente maria = salvarERecarregar("Maria Silva", "maria@exemplo.com");
        Instant criadoEmOriginal = maria.getCriadoEm();
        String email160 = emailValidoComTamanho(160);
        assertEquals(160, email160.length());

        executarPatchEmail(maria.getId(), payloadEmail("  " + email160 + "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(maria.getId()))
                .andExpect(jsonPath("$.nome").value("Maria Silva"))
                .andExpect(jsonPath("$.email").value(email160))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.criadoEm").value(criadoEmOriginal.toString()));

        Cliente atualizado = repository.findById(maria.getId()).orElseThrow();
        assertEquals(email160, atualizado.getEmail());
        assertEquals("Maria Silva", atualizado.getNome());
        assertTrue(atualizado.isAtivo());
        assertEquals(criadoEmOriginal, atualizado.getCriadoEm());
    }

    @Test
    @DisplayName("Deve manter idempotência quando próprio e-mail vier com espaços laterais e caixa diferente")
    void deveTratarIdempotenciaComEspacosLateraisECaixaDiferente() throws Exception {
        Cliente maria = salvarERecarregar("Maria Silva", "maria@exemplo.com");
        Instant criadoEmOriginal = maria.getCriadoEm();

        executarPatchEmail(maria.getId(), payloadEmail("  MARIA@EXEMPLO.COM  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(maria.getId()))
                .andExpect(jsonPath("$.nome").value("Maria Silva"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.criadoEm").value(criadoEmOriginal.toString()));

        Cliente atualizado = repository.findById(maria.getId()).orElseThrow();
        assertTrue("maria@exemplo.com".equalsIgnoreCase(atualizado.getEmail()));
        assertEquals("Maria Silva", atualizado.getNome());
        assertTrue(atualizado.isAtivo());
        assertEquals(criadoEmOriginal, atualizado.getCriadoEm());
    }

    @Test
    @DisplayName("Deve aceitar e-mail válido com caracteres especiais permitidos no local-part")
    void deveAceitarEmailComCaracteresEspeciaisValidosNoLocalPart() throws Exception {
        Cliente maria = salvarERecarregar("Maria Silva", "maria@exemplo.com");
        String emailEspecial = "maria.silva+portal_2026-01@sub.exemplo.com.br";

        executarPatchEmail(maria.getId(), payloadEmail(emailEspecial))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(emailEspecial))
                .andExpect(jsonPath("$.nome").value("Maria Silva"));

        assertEquals(emailEspecial, repository.findById(maria.getId()).orElseThrow().getEmail());
    }

    @Test
    @DisplayName("Deve retornar HTTP 400 ProblemDetail para identificador {id} não numérico ou overflow de Long (SC-17)")
    void deveRetornar400ProblemDetailParaIdNaoNumericoOuOverflow() throws Exception {
        assertProblemDetail(executarPatchEmail("abc", payloadEmail("maria.nova@exemplo.com")), 400);
        assertProblemDetail(executarPatchEmail("99999999999999999999", payloadEmail("maria.nova@exemplo.com")), 400);
    }

    @Test
    @DisplayName("Deve retornar HTTP 404 ProblemDetail para ids numéricos limítrofes inexistentes")
    void deveRetornar404ProblemDetailParaIdsLimitrofesInexistentes() throws Exception {
        assertProblemDetail(executarPatchEmail(-1L, payloadEmail("maria.nova@exemplo.com")), 404);
        assertProblemDetail(executarPatchEmail(0L, payloadEmail("maria.nova@exemplo.com")), 404);
    }

    @Test
    @DisplayName("Deve retornar HTTP 400 ProblemDetail para corpo JSON sintaticamente malformado (SC-18)")
    void deveRetornar400ProblemDetailParaJsonMalformado() throws Exception {
        Cliente maria = salvarERecarregar("Maria Silva", "maria@exemplo.com");

        assertProblemDetail(executarPatchEmail(maria.getId(), "{\"email\": \"maria.nova@exemplo.com\""), 400);

        assertEquals("maria@exemplo.com", repository.findById(maria.getId()).orElseThrow().getEmail());
    }

    @Test
    @DisplayName("Deve confirmar contrato ClienteResponse e atualização parcial via GET subsequente (SC-19)")
    void deveConfirmarContratoClienteResponseEAtualizacaoParcialViaGetSubsequente() throws Exception {
        Cliente maria = salvarERecarregar("Maria Silva", "maria@exemplo.com");
        Instant criadoEmOriginal = maria.getCriadoEm();

        executarPatchEmail(maria.getId(), payloadEmail("maria.contrato@exemplo.com"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(maria.getId()))
                .andExpect(jsonPath("$.nome").value("Maria Silva"))
                .andExpect(jsonPath("$.email").value("maria.contrato@exemplo.com"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.criadoEm").value(criadoEmOriginal.toString()));

        mvc.perform(get("/api/v1/clientes/{id}", maria.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(maria.getId()))
                .andExpect(jsonPath("$.nome").value("Maria Silva"))
                .andExpect(jsonPath("$.email").value("maria.contrato@exemplo.com"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.criadoEm").value(criadoEmOriginal.toString()));
    }

    @Test
    @DisplayName("Deve retornar respostas de erro 400, 404 e 409 no formato RFC 9457 ProblemDetail (SC-20)")
    void deveRetornarErrosNoFormatoRfc9457ProblemDetail() throws Exception {
        Cliente maria = salvarERecarregar("Maria Silva", "maria@exemplo.com");
        salvarERecarregar("Outro Cliente", "Outro.Cliente@Exemplo.com");

        assertProblemDetail(executarPatchEmail(maria.getId(), payloadEmail("email-invalido")), 400);
        assertProblemDetail(executarPatchEmail(999L, payloadEmail("qualquer@exemplo.com")), 404);
        assertProblemDetail(executarPatchEmail(maria.getId(), payloadEmail("outro.cliente@exemplo.com")), 409);
    }

    @Test
    @DisplayName("Deve retornar HTTP 405 para métodos HTTP não suportados na rota /email preservando cliente (SC-21)")
    void deveRetornar405ParaMetodosNaoSuportadosNaRotaDeEmail() throws Exception {
        Cliente maria = salvarERecarregar("Maria Silva", "maria@exemplo.com");
        String body = payloadEmail("maria.nova@exemplo.com");

        mvc.perform(put("/api/v1/clientes/{id}/email", maria.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/api/v1/clientes/{id}/email", maria.getId()))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/v1/clientes/{id}/email", maria.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/v1/clientes/{id}/email", maria.getId()))
                .andExpect(status().isMethodNotAllowed());

        assertEquals("maria@exemplo.com", repository.findById(maria.getId()).orElseThrow().getEmail());
    }

    @Test
    @DisplayName("Deve retornar HTTP 415 quando Content-Type não for application/json")
    void deveRetornar415ParaContentTypeNaoJson() throws Exception {
        Cliente maria = salvarERecarregar("Maria Silva", "maria@exemplo.com");

        mvc.perform(patch("/api/v1/clientes/{id}/email", maria.getId())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("maria.nova@exemplo.com"))
                .andExpect(status().isUnsupportedMediaType());

        assertEquals("maria@exemplo.com", repository.findById(maria.getId()).orElseThrow().getEmail());
    }

    @Test
    @DisplayName("Deve impedir sobrescrita de nome e ativo quando payload contiver campos extras")
    void deveImpedirSobrescritaDeCamposExtrasNoJson() throws Exception {
        Cliente maria = salvarERecarregar("Maria Silva", "maria@exemplo.com");

        MvcResult result = executarPatchEmail(
                maria.getId(),
                "{\"email\":\"maria.nova@exemplo.com\",\"nome\":\"Nome Indevido\",\"ativo\":false}")
                .andReturn();

        int status = result.getResponse().getStatus();
        assertTrue(status == 200 || status == 400);

        Cliente persistido = repository.findById(maria.getId()).orElseThrow();
        assertEquals("Maria Silva", persistido.getNome());
        assertTrue(persistido.isAtivo());
    }

    @Test
    @DisplayName("Deve rejeitar tentativa de injeção SQL no campo email sem vazamento interno (SC-22)")
    void deveRejeitarTentativaDeSqlInjectionNoCampoEmailSemVazamento() throws Exception {
        Cliente maria = salvarERecarregar("Maria Silva", "maria@exemplo.com");

        MvcResult result = assertProblemDetail(
                executarPatchEmail(maria.getId(), payloadEmail("maria' OR '1'='1--%_@exemplo.com")),
                400);

        assertSemVazamentoInterno(result);
        assertEquals("maria@exemplo.com", repository.findById(maria.getId()).orElseThrow().getEmail());
    }

    @Test
    @DisplayName("Deve rejeitar tentativa de injeção no parâmetro de rota {id} sem vazamento interno (SC-23)")
    void deveRejeitarTentativaDeInjecaoNoParametroDeRotaIdSemVazamento() throws Exception {
        MvcResult result = assertProblemDetail(
                executarPatchEmail("1' OR 1=1", payloadEmail("maria.nova@exemplo.com")),
                400);

        assertSemVazamentoInterno(result);
    }
}
