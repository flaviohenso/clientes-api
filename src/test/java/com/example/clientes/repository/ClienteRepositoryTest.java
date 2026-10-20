package com.example.clientes.repository;

import com.example.clientes.domain.Cliente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ClienteRepositoryTest {

    @Autowired
    private ClienteRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.save(new Cliente("Cliente Exemplo", "cliente@exemplo.com"));
    }

    @Test
    @DisplayName("Deve encontrar cliente ao consultar por e-mail com correspondência exata (AC-1)")
    void deveEncontrarClientePorEmailComCorrespondenciaExata() {
        Page<Cliente> resultado = repository.findByEmailIgnoreCase("cliente@exemplo.com", PageRequest.of(0, 10));

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().getFirst().getEmail()).isEqualTo("cliente@exemplo.com");
    }

    @Test
    @DisplayName("Deve encontrar cliente ao consultar por e-mail com variação de maiúsculas e minúsculas (AC-2)")
    void deveEncontrarClientePorEmailIgnorandoMaiusculasEMinusculas() {
        Page<Cliente> resultado = repository.findByEmailIgnoreCase("CLIENTE@EXEMPLO.COM", PageRequest.of(0, 10));

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().getFirst().getEmail()).isEqualTo("cliente@exemplo.com");
    }

    @Test
    @DisplayName("Deve retornar página vazia ao buscar por e-mail inexistente (AC-3)")
    void deveRetornarPaginaVaziaQuandoEmailNaoExistir() {
        Page<Cliente> resultado = repository.findByEmailIgnoreCase("inexistente@exemplo.com", PageRequest.of(0, 10));

        assertThat(resultado.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Não deve retornar cliente ao buscar por trecho parcial de e-mail (AC-5)")
    void naoDeveRetornarClienteQuandoBuscaForPorTrechoParcialDoEmail() {
        Page<Cliente> resultadoDominio = repository.findByEmailIgnoreCase("exemplo.com", PageRequest.of(0, 10));
        Page<Cliente> resultadoIncompleto = repository.findByEmailIgnoreCase("cliente@exemplo.co", PageRequest.of(0, 10));

        assertThat(resultadoDominio.isEmpty()).isTrue();
        assertThat(resultadoIncompleto.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Deve retornar apenas clientes ativos ao consultar por status ativo verdadeiro (AC-4)")
    void deveRetornarApenasClientesAtivosAoConsultarPorStatusAtivoVerdadeiro() {
        Cliente inativo = new Cliente("Cliente Inativo", "inativo@exemplo.com");
        inativo.inativar();
        repository.save(inativo);

        Page<Cliente> resultado = repository.findByAtivo(true, PageRequest.of(0, 10));

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().getFirst().isAtivo()).isTrue();
        assertThat(resultado.getContent().getFirst().getEmail()).isEqualTo("cliente@exemplo.com");
    }

    @Test
    @DisplayName("Deve retornar apenas clientes inativos ao consultar por status ativo falso (AC-5)")
    void deveRetornarApenasClientesInativosAoConsultarPorStatusAtivoFalso() {
        Cliente inativo = new Cliente("Cliente Inativo", "inativo@exemplo.com");
        inativo.inativar();
        repository.save(inativo);

        Page<Cliente> resultado = repository.findByAtivo(false, PageRequest.of(0, 10));

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().getFirst().isAtivo()).isFalse();
        assertThat(resultado.getContent().getFirst().getEmail()).isEqualTo("inativo@exemplo.com");
    }

    @Test
    @DisplayName("Deve retornar todos os clientes sem filtrar por status ao consultar com findAll (AC-6)")
    void deveRetornarTodosOsClientesSemFiltrarPorStatus() {
        Cliente inativo = new Cliente("Cliente Inativo", "inativo@exemplo.com");
        inativo.inativar();
        repository.save(inativo);

        Page<Cliente> resultado = repository.findAll(PageRequest.of(0, 10));

        assertThat(resultado.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("Deve filtrar clientes por nome e status de ativação")
    void deveFiltrarClientesPorNomeEStatusAtivacao() {
        Cliente inativo = new Cliente("Cliente Exemplo Inativo", "inativo.nome@exemplo.com");
        inativo.inativar();
        repository.save(inativo);

        Page<Cliente> resultadoAtivos = repository.findByNomeContainingIgnoreCaseAndAtivo("Exemplo", true, PageRequest.of(0, 10));
        Page<Cliente> resultadoInativos = repository.findByNomeContainingIgnoreCaseAndAtivo("Exemplo", false, PageRequest.of(0, 10));

        assertThat(resultadoAtivos.getContent()).hasSize(1);
        assertThat(resultadoAtivos.getContent().getFirst().isAtivo()).isTrue();
        assertThat(resultadoAtivos.getContent().getFirst().getEmail()).isEqualTo("cliente@exemplo.com");

        assertThat(resultadoInativos.getContent()).hasSize(1);
        assertThat(resultadoInativos.getContent().getFirst().isAtivo()).isFalse();
        assertThat(resultadoInativos.getContent().getFirst().getEmail()).isEqualTo("inativo.nome@exemplo.com");
    }

    @Test
    @DisplayName("Deve filtrar clientes por e-mail e status de ativação")
    void deveFiltrarClientesPorEmailEStatusAtivacao() {
        Cliente inativo = new Cliente("Outro Cliente", "outro@exemplo.com");
        inativo.inativar();
        repository.save(inativo);

        Page<Cliente> resultadoAtivo = repository.findByEmailIgnoreCaseAndAtivo("cliente@exemplo.com", true, PageRequest.of(0, 10));
        Page<Cliente> resultadoInativoIncompativel = repository.findByEmailIgnoreCaseAndAtivo("cliente@exemplo.com", false, PageRequest.of(0, 10));

        assertThat(resultadoAtivo.getContent()).hasSize(1);
        assertThat(resultadoAtivo.getContent().getFirst().getEmail()).isEqualTo("cliente@exemplo.com");
        assertThat(resultadoInativoIncompativel.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Deve contar todos os clientes cadastrados")
    void deveContarTodosOsClientesCadastrados() {
        Cliente inativo = new Cliente("Cliente Inativo", "inativo@exemplo.com");
        inativo.inativar();
        repository.save(inativo);

        long total = repository.count();

        assertThat(total).isEqualTo(2);
    }

    @Test
    @DisplayName("Deve contar clientes filtrando por status ativo")
    void deveContarClientesFiltrandoPorStatusAtivo() {
        Cliente inativo1 = new Cliente("Cliente Inativo 1", "inativo1@exemplo.com");
        inativo1.inativar();
        repository.save(inativo1);

        Cliente inativo2 = new Cliente("Cliente Inativo 2", "inativo2@exemplo.com");
        inativo2.inativar();
        repository.save(inativo2);

        long totalAtivos = repository.countByAtivo(true);
        long totalInativos = repository.countByAtivo(false);

        assertThat(totalAtivos).isEqualTo(1);
        assertThat(totalInativos).isEqualTo(2);
    }

    @Test
    @DisplayName("Deve retornar zero na contagem quando a base estiver vazia")
    void deveRetornarZeroNaContagemQuandoBaseEstiverVazia() {
        repository.deleteAll();

        long total = repository.count();
        long totalAtivos = repository.countByAtivo(true);
        long totalInativos = repository.countByAtivo(false);

        assertThat(total).isZero();
        assertThat(totalAtivos).isZero();
        assertThat(totalInativos).isZero();
    }
}
