package com.example.clientes;

import com.example.clientes.domain.Cliente;
import com.example.clientes.repository.ClienteRepository;
import com.example.clientes.service.ClienteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClienteIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ClienteRepository repository;

    @Autowired
    private ClienteService service;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.save(new Cliente("Cliente Exemplo", "cliente@exemplo.com"));
    }

    private void popularClientesParaContagem() {
        repository.deleteAll();
        for (int i = 1; i <= 5; i++) {
            repository.save(new Cliente("Cliente Ativo " + i, "ativo" + i + "@exemplo.com"));
        }
        for (int i = 1; i <= 3; i++) {
            Cliente inativo = repository.save(new Cliente("Cliente Inativo " + i, "inativo" + i + "@exemplo.com"));
            service.inativar(inativo.getId());
        }
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

    private ResultActions executarPatchEmail(Long id, String payloadJson) throws Exception {
        return mvc.perform(patch("/api/v1/clientes/{id}/email", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payloadJson));
    }

    private void assertPatchEmailInvalidoPreservaEmail(String payloadJson) throws Exception {
        Cliente maria = salvarERecarregar("Maria Silva", "maria@exemplo.com");

        executarPatchEmail(maria.getId(), payloadJson)
                .andExpect(status().isBadRequest());

        Cliente persistido = repository.findById(maria.getId()).orElseThrow();
        assertEquals("maria@exemplo.com", persistido.getEmail());
    }

    @Test
    @DisplayName("Deve retornar cliente correspondente ao filtrar por e-mail exato (AC-1)")
    void deveEncontrarClientePorEmailExato() throws Exception {
        mvc.perform(get("/api/v1/clientes").param("email", "cliente@exemplo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("cliente@exemplo.com"));
    }

    @Test
    @DisplayName("Deve retornar cliente ao filtrar por e-mail com maiúsculas/minúsculas (AC-2)")
    void deveEncontrarClientePorEmailIgnorandoMaiusculasEMinusculas() throws Exception {
        mvc.perform(get("/api/v1/clientes").param("email", "CLIENTE@EXEMPLO.COM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("cliente@exemplo.com"));
    }

    @Test
    @DisplayName("Deve retornar lista vazia ao filtrar por e-mail inexistente (AC-3)")
    void deveRetornarListaVaziaParaEmailInexistente() throws Exception {
        mvc.perform(get("/api/v1/clientes").param("email", "inexistente@exemplo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @DisplayName("Deve retornar HTTP 400 ProblemDetail para e-mail com formato inválido (AC-4)")
    void deveRetornar400ParaEmailComFormatoInvalido() throws Exception {
        mvc.perform(get("/api/v1/clientes").param("email", "email-invalido"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Não deve retornar cliente ao buscar por trecho parcial de e-mail válido (AC-5)")
    void naoDeveRetornarClienteParaTrechoParcialDeEmail() throws Exception {
        mvc.perform(get("/api/v1/clientes").param("email", "cliente@exemplo.co"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @DisplayName("Deve reativar cliente inativo com sucesso e retornar HTTP 200 (AC-1)")
    void deveReativarClienteInativoComSucesso() throws Exception {
        repository.deleteAll();
        Cliente inativo = repository.save(new Cliente("Cliente Inativo", "inativo@exemplo.com"));
        service.inativar(inativo.getId());

        mvc.perform(patch("/api/v1/clientes/{id}/reativar", inativo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(inativo.getId()))
                .andExpect(jsonPath("$.ativo").value(true));

        mvc.perform(get("/api/v1/clientes/{id}", inativo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @DisplayName("Deve manter status ativo de forma idempotente ao reativar cliente já ativo (AC-2)")
    void deveReativarClienteJaAtivoDeFormaIdempotente() throws Exception {
        Cliente ativo = repository.save(new Cliente("Cliente Ativo", "ativo@exemplo.com"));

        mvc.perform(patch("/api/v1/clientes/{id}/reativar", ativo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ativo.getId()))
                .andExpect(jsonPath("$.ativo").value(true));

        mvc.perform(patch("/api/v1/clientes/{id}/reativar", ativo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ativo.getId()))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @DisplayName("Deve retornar HTTP 404 ProblemDetail ao tentar reativar cliente com id inexistente (AC-3)")
    void deveRetornar404AoReativarClienteInexistente() throws Exception {
        mvc.perform(patch("/api/v1/clientes/{id}/reativar", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Cliente 999 não encontrado"));
    }

    @Test
    @DisplayName("Deve retornar apenas clientes ativos ao filtrar por ativo=true (AC-4)")
    void deveListarApenasClientesAtivosComFiltroAtivoTrue() throws Exception {
        repository.deleteAll();
        Cliente ativo = repository.save(new Cliente("Cliente Ativo", "ativo@exemplo.com"));
        Cliente inativo = repository.save(new Cliente("Cliente Inativo", "inativo@exemplo.com"));
        service.inativar(inativo.getId());

        mvc.perform(get("/api/v1/clientes").param("ativo", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(ativo.getId()))
                .andExpect(jsonPath("$.content[0].ativo").value(true));
    }

    @Test
    @DisplayName("Deve retornar apenas clientes inativos ao filtrar por ativo=false (AC-5)")
    void deveListarApenasClientesInativosComFiltroAtivoFalse() throws Exception {
        repository.deleteAll();
        Cliente ativo = repository.save(new Cliente("Cliente Ativo", "ativo@exemplo.com"));
        Cliente inativo = repository.save(new Cliente("Cliente Inativo", "inativo@exemplo.com"));
        service.inativar(inativo.getId());

        mvc.perform(get("/api/v1/clientes").param("ativo", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(inativo.getId()))
                .andExpect(jsonPath("$.content[0].ativo").value(false));
    }

    @Test
    @DisplayName("Deve listar todos os clientes quando o parâmetro ativo não for informado (AC-6)")
    void deveListarTodosOsClientesSemFiltroAtivo() throws Exception {
        repository.deleteAll();
        Cliente ativo = repository.save(new Cliente("Cliente Ativo", "ativo@exemplo.com"));
        Cliente inativo = repository.save(new Cliente("Cliente Inativo", "inativo@exemplo.com"));
        service.inativar(inativo.getId());

        mvc.perform(get("/api/v1/clientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    @DisplayName("Deve retornar contagem total de clientes sem filtro de status ativo (AC-1)")
    void deveRetornarContagemTotalDeClientesSemFiltro() throws Exception {
        popularClientesParaContagem();

        mvc.perform(get("/api/v1/clientes/contagem"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(8));
    }

    @Test
    @DisplayName("Deve retornar contagem apenas de clientes ativos quando ativo=true (AC-2)")
    void deveRetornarContagemDeClientesAtivos() throws Exception {
        popularClientesParaContagem();

        mvc.perform(get("/api/v1/clientes/contagem").param("ativo", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5));
    }

    @Test
    @DisplayName("Deve retornar contagem apenas de clientes inativos quando ativo=false (AC-3)")
    void deveRetornarContagemDeClientesInativos() throws Exception {
        popularClientesParaContagem();

        mvc.perform(get("/api/v1/clientes/contagem").param("ativo", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    @DisplayName("Deve retornar contagem zero quando a base de clientes estiver vazia (AC-4)")
    void deveRetornarContagemZeroQuandoBaseEstiverVazia() throws Exception {
        repository.deleteAll();

        mvc.perform(get("/api/v1/clientes/contagem"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    @DisplayName("Deve retornar HTTP 400 Bad Request quando parâmetro ativo for inválido (AC-5)")
    void deveRetornar400QuandoParametroAtivoForInvalido() throws Exception {
        mvc.perform(get("/api/v1/clientes/contagem").param("ativo", "invalido"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Deve retornar existe=true para e-mail cadastrado com correspondência exata (CLI-27 AC-1)")
    void deveRetornarExisteTrueParaEmailCadastrado() throws Exception {
        repository.save(new Cliente("Maria", "maria@exemplo.com"));

        mvc.perform(get("/api/v1/clientes/email-existe").param("email", "maria@exemplo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existe").value(true));
    }

    @Test
    @DisplayName("Deve retornar existe=false para e-mail inexistente (CLI-27 AC-2)")
    void deveRetornarExisteFalseParaEmailInexistente() throws Exception {
        repository.save(new Cliente("Maria", "maria@exemplo.com"));

        mvc.perform(get("/api/v1/clientes/email-existe").param("email", "naoexiste@exemplo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existe").value(false));
    }

    @Test
    @DisplayName("Deve retornar existe=true ignorando maiúsculas e minúsculas (CLI-27 AC-3)")
    void deveRetornarExisteTrueIgnorandoCaixa() throws Exception {
        repository.save(new Cliente("Maria", "maria@exemplo.com"));

        mvc.perform(get("/api/v1/clientes/email-existe").param("email", "MARIA@Exemplo.COM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existe").value(true));
    }

    @Test
    @DisplayName("Deve retornar HTTP 400 quando o parâmetro email não for informado (CLI-27 AC-4)")
    void deveRetornar400QuandoEmailNaoInformado() throws Exception {
        mvc.perform(get("/api/v1/clientes/email-existe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Deve retornar HTTP 400 quando o parâmetro email estiver em branco (CLI-27)")
    void deveRetornar400QuandoEmailEmBranco() throws Exception {
        mvc.perform(get("/api/v1/clientes/email-existe").param("email", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Deve atualizar apenas o e-mail preservando nome, ativo e criadoEm (CLI-28 AC-1)")
    void deveAtualizarEmailComSucessoPreservandoNomeAtivoECriadoEm() throws Exception {
        Cliente maria = salvarERecarregar("Maria Silva", "maria@exemplo.com");
        Instant criadoEmOriginal = maria.getCriadoEm();

        executarPatchEmail(maria.getId(), payloadEmail("maria.nova@exemplo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(maria.getId()))
                .andExpect(jsonPath("$.nome").value("Maria Silva"))
                .andExpect(jsonPath("$.email").value("maria.nova@exemplo.com"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.criadoEm").value(criadoEmOriginal.toString()));

        Cliente atualizado = repository.findById(maria.getId()).orElseThrow();
        assertEquals("maria.nova@exemplo.com", atualizado.getEmail());
        assertEquals("Maria Silva", atualizado.getNome());
        assertTrue(atualizado.isAtivo());
        assertEquals(criadoEmOriginal, atualizado.getCriadoEm());
    }

    @Test
    @DisplayName("Deve remover espaços laterais do e-mail antes da persistência (CLI-28 AC-2)")
    void deveRemoverEspacosLateraisAntesDeValidarDuplicidadeEPersistir() throws Exception {
        Cliente maria = salvarERecarregar("Maria Silva", "maria@exemplo.com");
        Instant criadoEmOriginal = maria.getCriadoEm();

        executarPatchEmail(maria.getId(), payloadEmail("  nova.com.espacos@exemplo.com  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(maria.getId()))
                .andExpect(jsonPath("$.nome").value("Maria Silva"))
                .andExpect(jsonPath("$.email").value("nova.com.espacos@exemplo.com"))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$.criadoEm").value(criadoEmOriginal.toString()));

        Cliente atualizado = repository.findById(maria.getId()).orElseThrow();
        assertEquals("nova.com.espacos@exemplo.com", atualizado.getEmail());
        assertEquals("Maria Silva", atualizado.getNome());
        assertTrue(atualizado.isAtivo());
        assertEquals(criadoEmOriginal, atualizado.getCriadoEm());
    }

    @Test
    @DisplayName("Deve retornar 409 Conflict após trim quando e-mail com espaços laterais já pertencer a outro cliente (CLI-28 AC-2)")
    void deveRejeitar409QuandoNovoEmailComEspacosLateraisPertencerAOutroCliente() throws Exception {
        Cliente maria = salvarERecarregar("Maria Silva", "maria@exemplo.com");
        salvarERecarregar("Outro Cliente", "Outro.Cliente@Exemplo.com");

        executarPatchEmail(maria.getId(), payloadEmail("  outro.cliente@exemplo.com  "))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").isNotEmpty());

        Cliente persistido = repository.findById(maria.getId()).orElseThrow();
        assertEquals("maria@exemplo.com", persistido.getEmail());
    }

    @Test
    @DisplayName("Deve tratar atualização com mesmo e-mail case-insensitive de forma idempotente retornando 200 OK (CLI-28 AC-3)")
    void deveTratarAtualizacaoComMesmoEmailCaseInsensitiveDeFormaIdempotente() throws Exception {
        Cliente maria = salvarERecarregar("Maria Silva", "maria@exemplo.com");
        Instant criadoEmOriginal = maria.getCriadoEm();

        executarPatchEmail(maria.getId(), payloadEmail("MARIA@EXEMPLO.COM"))
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
    @DisplayName("Deve permitir alteração de e-mail de cliente inativo mantendo ativo=false (CLI-28 AC-4)")
    void deveAtualizarEmailDeClienteInativoMantendoAtivoFalse() throws Exception {
        Cliente salvo = repository.save(new Cliente("João Souza", "joao@exemplo.com"));
        service.inativar(salvo.getId());
        Cliente joaoInativo = repository.findById(salvo.getId()).orElseThrow();
        Instant criadoEmOriginal = joaoInativo.getCriadoEm();
        assertFalse(joaoInativo.isAtivo());

        executarPatchEmail(joaoInativo.getId(), payloadEmail("joao.atualizado@exemplo.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(joaoInativo.getId()))
                .andExpect(jsonPath("$.nome").value("João Souza"))
                .andExpect(jsonPath("$.email").value("joao.atualizado@exemplo.com"))
                .andExpect(jsonPath("$.ativo").value(false))
                .andExpect(jsonPath("$.criadoEm").value(criadoEmOriginal.toString()));

        Cliente atualizado = repository.findById(joaoInativo.getId()).orElseThrow();
        assertEquals("joao.atualizado@exemplo.com", atualizado.getEmail());
        assertEquals("João Souza", atualizado.getNome());
        assertFalse(atualizado.isAtivo());
        assertEquals(criadoEmOriginal, atualizado.getCriadoEm());
    }

    @Test
    @DisplayName("Deve retornar 409 Conflict ProblemDetail por duplicidade case-insensitive preservando e-mail original (CLI-28 AC-5)")
    void deveRetornar409ConflictProblemDetailPorDuplicidadeCaseInsensitive() throws Exception {
        Cliente maria = salvarERecarregar("Maria Silva", "maria@exemplo.com");
        salvarERecarregar("Outro Cliente", "Outro.Cliente@Exemplo.com");

        executarPatchEmail(maria.getId(), payloadEmail("outro.cliente@exemplo.com"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").isNotEmpty());

        Cliente persistido = repository.findById(maria.getId()).orElseThrow();
        assertEquals("maria@exemplo.com", persistido.getEmail());
    }

    @Test
    @DisplayName("Deve retornar 404 Not Found ProblemDetail quando id não existir (CLI-28 AC-6)")
    void deveRetornar404NotFoundProblemDetailParaClienteInexistente() throws Exception {
        executarPatchEmail(999L, payloadEmail("qualquer@exemplo.com"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Cliente 999 não encontrado"));
    }

    @Test
    @DisplayName("Deve retornar 404 Not Found para id inexistente mesmo quando e-mail pertencer a terceiro (CLI-28 AC-6)")
    void deveRetornar404MesmoQuandoEmailPertencerATerceiroParaIdInexistente() throws Exception {
        assertTrue(repository.existsByEmailIgnoreCase("cliente@exemplo.com"));

        executarPatchEmail(999L, payloadEmail("cliente@exemplo.com"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Cliente 999 não encontrado"));
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request e preservar e-mail quando campo email estiver ausente (CLI-28 AC-7)")
    void deveRetornar400EPreservarEmailQuandoCampoEmailEstiverAusente() throws Exception {
        assertPatchEmailInvalidoPreservaEmail("{}");
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request e preservar e-mail quando campo email for nulo (CLI-28 AC-7)")
    void deveRetornar400EPreservarEmailQuandoCampoEmailForNulo() throws Exception {
        assertPatchEmailInvalidoPreservaEmail("{\"email\":null}");
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request e preservar e-mail quando campo email for vazio (CLI-28 AC-7)")
    void deveRetornar400EPreservarEmailQuandoCampoEmailForVazio() throws Exception {
        assertPatchEmailInvalidoPreservaEmail(payloadEmail(""));
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request e preservar e-mail quando campo email contiver apenas espaços (CLI-28 AC-7)")
    void deveRetornar400EPreservarEmailQuandoCampoEmailContiverApenasEspacos() throws Exception {
        assertPatchEmailInvalidoPreservaEmail(payloadEmail("   "));
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request e preservar e-mail quando e-mail tiver formato inválido (CLI-28 AC-8)")
    void deveRetornar400EPreservarEmailQuandoEmailTiverFormatoInvalido() throws Exception {
        assertPatchEmailInvalidoPreservaEmail(payloadEmail("email-invalido"));
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request e preservar e-mail quando e-mail válido possuir 161 caracteres (CLI-28 AC-9)")
    void deveRetornar400EPreservarEmailQuandoEmailTiver161Caracteres() throws Exception {
        assertPatchEmailInvalidoPreservaEmail(payloadEmail(emailValidoComTamanho(161)));
    }

    @Test
    @DisplayName("Deve atualizar e-mail válido com exatos 160 caracteres retornando 200 OK (CLI-28 AC-10)")
    void deveAtualizarEmailValidoComExatos160CaracteresRetornando200() throws Exception {
        Cliente maria = salvarERecarregar("Maria Silva", "maria@exemplo.com");
        Instant criadoEmOriginal = maria.getCriadoEm();
        String email160 = emailValidoComTamanho(160);

        executarPatchEmail(maria.getId(), payloadEmail(email160))
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
}
