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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClienteInativarLoteQaIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ClienteRepository repository;

    @Autowired
    private ClienteService service;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    private Cliente salvarClienteAtivo(String nome, String email) {
        return repository.save(new Cliente(nome, email));
    }

    private Cliente salvarClienteInativo(String nome, String email) {
        Cliente salvo = repository.save(new Cliente(nome, email));
        service.inativar(salvo.getId());
        return repository.findById(salvo.getId()).orElseThrow();
    }

    private List<Cliente> salvarClientesAtivos(int quantidade) {
        List<Cliente> novos = new ArrayList<>(quantidade);
        for (int i = 1; i <= quantidade; i++) {
            novos.add(new Cliente("Cliente QA " + i, "qa.lote." + i + "@exemplo.com"));
        }
        List<Cliente> salvos = new ArrayList<>(quantidade);
        repository.saveAll(novos).forEach(salvos::add);
        return salvos;
    }

    private static String payloadInativarLote(List<Long> ids) {
        String itens = ids.stream()
                .map(id -> id == null ? "null" : id.toString())
                .collect(Collectors.joining(", "));
        return "{\"ids\":[%s]}".formatted(itens);
    }

    private ResultActions executarPatchInativarLote(String payloadJson) throws Exception {
        return mvc.perform(patch("/api/v1/clientes/inativar-lote")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payloadJson));
    }

    @Test
    @DisplayName("SC-3: Deve processar de forma idempotente quando todos os clientes do lote já estiverem inativos")
    void deveProcessarDeFormaIdempotenteQuandoTodosClientesJaEstiveremInativos() throws Exception {
        Cliente c1 = salvarClienteInativo("Cliente Inativo 1", "inativo1@exemplo.com");
        Cliente c2 = salvarClienteInativo("Cliente Inativo 2", "inativo2@exemplo.com");

        String payload = payloadInativarLote(List.of(c1.getId(), c2.getId()));

        executarPatchInativarLote(payload)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProcessados").value(2))
                .andExpect(jsonPath("$.idsInativados", hasSize(2)))
                .andExpect(jsonPath("$.idsInativados[0]").value(c1.getId()))
                .andExpect(jsonPath("$.idsInativados[1]").value(c2.getId()));

        executarPatchInativarLote(payload)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProcessados").value(2))
                .andExpect(jsonPath("$.idsInativados", hasSize(2)))
                .andExpect(jsonPath("$.idsInativados[0]").value(c1.getId()))
                .andExpect(jsonPath("$.idsInativados[1]").value(c2.getId()));

        assertFalse(repository.findById(c1.getId()).orElseThrow().isAtivo());
        assertFalse(repository.findById(c2.getId()).orElseThrow().isAtivo());
    }

    @Test
    @DisplayName("SC-5: Deve preservar a ordem de primeira ocorrência na desduplicação de IDs fora de sequência")
    void devePreservarOrdemDePrimeiraOcorrenciaNaDesduplicacaoEndToEnd() throws Exception {
        Cliente c1 = salvarClienteAtivo("Cliente 1", "c1@exemplo.com");
        Cliente c2 = salvarClienteAtivo("Cliente 2", "c2@exemplo.com");
        Cliente c3 = salvarClienteAtivo("Cliente 3", "c3@exemplo.com");

        String payload = payloadInativarLote(List.of(c3.getId(), c1.getId(), c3.getId(), c2.getId(), c1.getId()));

        executarPatchInativarLote(payload)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProcessados").value(3))
                .andExpect(jsonPath("$.idsInativados", hasSize(3)))
                .andExpect(jsonPath("$.idsInativados[0]").value(c3.getId()))
                .andExpect(jsonPath("$.idsInativados[1]").value(c1.getId()))
                .andExpect(jsonPath("$.idsInativados[2]").value(c2.getId()));

        assertFalse(repository.findById(c1.getId()).orElseThrow().isAtivo());
        assertFalse(repository.findById(c2.getId()).orElseThrow().isAtivo());
        assertFalse(repository.findById(c3.getId()).orElseThrow().isAtivo());
    }

    @Test
    @DisplayName("SC-7: Deve garantir rollback atômico e HTTP 404 ProblemDetail quando ID inexistente estiver no fim do lote")
    void deveGarantirRollbackAtomicoQuandoIdInexistenteEstiverNoFimDoLote() throws Exception {
        Cliente c1 = salvarClienteAtivo("Cliente 1", "c1@exemplo.com");
        Cliente c2 = salvarClienteAtivo("Cliente 2", "c2@exemplo.com");
        long idInexistente = Math.max(c1.getId(), c2.getId()) + 999L;

        executarPatchInativarLote(payloadInativarLote(List.of(c1.getId(), c2.getId(), idInexistente)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").value("Cliente %d não encontrado".formatted(idInexistente)));

        assertTrue(repository.findById(c1.getId()).orElseThrow().isAtivo());
        assertTrue(repository.findById(c2.getId()).orElseThrow().isAtivo());

        mvc.perform(get("/api/v1/clientes/{id}", c1.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));
        mvc.perform(get("/api/v1/clientes/{id}", c2.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    @DisplayName("SC-15: Deve inativar com sucesso lote com exatamente 1 identificador")
    void deveInativarComSucessoLoteComExatamenteUmIdentificador() throws Exception {
        Cliente c1 = salvarClienteAtivo("Cliente Único", "unico@exemplo.com");

        executarPatchInativarLote(payloadInativarLote(List.of(c1.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProcessados").value(1))
                .andExpect(jsonPath("$.idsInativados", hasSize(1)))
                .andExpect(jsonPath("$.idsInativados[0]").value(c1.getId()));

        assertFalse(repository.findById(c1.getId()).orElseThrow().isAtivo());
        mvc.perform(get("/api/v1/clientes/{id}", c1.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    @DisplayName("SC-16: Deve retornar HTTP 400 para JSON malformado, corpo vazio e tipo não numérico sem alterar dados")
    void deveRetornar400ParaJsonMalformadoCorpoVazioETipoNaoNumericoSemAlterarDados() throws Exception {
        Cliente c1 = salvarClienteAtivo("Cliente Preservado", "preservado@exemplo.com");

        executarPatchInativarLote("{\"ids\": [\"abc\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        executarPatchInativarLote("{\"ids\": [1, 2")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        executarPatchInativarLote("")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertTrue(repository.findById(c1.getId()).orElseThrow().isAtivo());
    }

    @Test
    @DisplayName("SC-17: Deve rejeitar atomicamente IDs numéricos não positivos (0 e -1) preservando clientes válidos")
    void deveRejeitarIdsNaoPositivosAtomicamentoSemAlterarClientesValidos() throws Exception {
        Cliente c1 = salvarClienteAtivo("Cliente Ativo", "ativo@exemplo.com");

        executarPatchInativarLote(payloadInativarLote(List.of(c1.getId(), 0L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Cliente 0 não encontrado"));

        assertTrue(repository.findById(c1.getId()).orElseThrow().isAtivo());

        executarPatchInativarLote(payloadInativarLote(List.of(c1.getId(), -1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Cliente -1 não encontrado"));

        assertTrue(repository.findById(c1.getId()).orElseThrow().isAtivo());
    }

    @Test
    @DisplayName("SC-18: Deve atender contrato de acesso aberto, resposta 200 estrita e ProblemDetail 404")
    void deveAtenderContratoAcessoAbertoEstrutura200EProblemDetail404() throws Exception {
        Cliente c1 = salvarClienteAtivo("Cliente 1", "c1@exemplo.com");
        Cliente c2 = salvarClienteAtivo("Cliente 2", "c2@exemplo.com");
        Cliente c3 = salvarClienteInativo("Cliente 3", "c3@exemplo.com");
        long idInexistente = Math.max(Math.max(c1.getId(), c2.getId()), c3.getId()) + 999L;

        executarPatchInativarLote(payloadInativarLote(List.of(c1.getId(), c3.getId(), c1.getId())))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.*", hasSize(2)))
                .andExpect(jsonPath("$.totalProcessados").value(2))
                .andExpect(jsonPath("$.idsInativados", hasSize(2)))
                .andExpect(jsonPath("$.idsInativados[0]").value(c1.getId()))
                .andExpect(jsonPath("$.idsInativados[1]").value(c3.getId()));

        executarPatchInativarLote(payloadInativarLote(List.of(c2.getId(), idInexistente)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").value("Cliente %d não encontrado".formatted(idInexistente)));

        assertTrue(repository.findById(c2.getId()).orElseThrow().isAtivo());
    }

    @Test
    @DisplayName("SC-19: Deve preservar endpoints individuais e contagem após inativação em lote")
    void devePreservarEndpointsIndividuaisEContagemAposInativacaoEmLote() throws Exception {
        Cliente c1 = salvarClienteAtivo("Cliente 1", "c1@exemplo.com");
        Cliente c2 = salvarClienteAtivo("Cliente 2", "c2@exemplo.com");
        Cliente c3 = salvarClienteAtivo("Cliente 3", "c3@exemplo.com");
        Cliente c4 = salvarClienteInativo("Cliente 4", "c4@exemplo.com");

        executarPatchInativarLote(payloadInativarLote(List.of(c1.getId(), c2.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProcessados").value(2));

        mvc.perform(get("/api/v1/clientes/contagem"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4));
        mvc.perform(get("/api/v1/clientes/contagem").param("ativo", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/v1/clientes/contagem").param("ativo", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));

        mvc.perform(delete("/api/v1/clientes/{id}", c3.getId()))
                .andExpect(status().isNoContent());

        mvc.perform(patch("/api/v1/clientes/{id}/reativar", c4.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));

        mvc.perform(get("/api/v1/clientes/contagem").param("ativo", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/v1/clientes/contagem").param("ativo", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    @DisplayName("SC-20: Deve rejeitar payloads com tentativa de injeção sem expor stacktrace ou alterar dados")
    void deveRejeitarPayloadsDeInjecaoSemExporStacktraceOuAlterarDados() throws Exception {
        Cliente c1 = salvarClienteAtivo("Cliente Seguro", "seguro@exemplo.com");

        MvcResult result = executarPatchInativarLote("{\"ids\": [\"1 OR 1=1\", \"<script>alert(1)</script>\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("at com.example."));
        assertFalse(body.contains("StackTrace"));
        assertFalse(body.contains("SELECT "));
        assertFalse(body.contains("UPDATE "));
        assertTrue(repository.findById(c1.getId()).orElseThrow().isAtivo());
    }

    @Test
    @DisplayName("SC-21: Deve aceitar 100 itens no payload com duplicatas e desduplicar para 50 IDs únicos")
    void deveAceitarExatamente100ItensComDuplicatasEDesduplicarPara50Unicos() throws Exception {
        List<Cliente> clientes50 = salvarClientesAtivos(50);
        List<Long> ids50 = clientes50.stream().map(Cliente::getId).toList();
        List<Long> ids100 = new ArrayList<>(100);
        ids100.addAll(ids50);
        ids100.addAll(ids50);

        executarPatchInativarLote(payloadInativarLote(ids100))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProcessados").value(50))
                .andExpect(jsonPath("$.idsInativados", hasSize(50)))
                .andExpect(jsonPath("$.idsInativados[0]").value(ids50.get(0)))
                .andExpect(jsonPath("$.idsInativados[49]").value(ids50.get(49)));

        assertEquals(50L, repository.countByAtivo(false));
        assertEquals(0L, repository.countByAtivo(true));
    }
}
