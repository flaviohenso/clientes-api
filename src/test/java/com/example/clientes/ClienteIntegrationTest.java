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
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
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
}
