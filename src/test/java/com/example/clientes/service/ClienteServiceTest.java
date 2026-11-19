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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    ClienteRepository repository;

    @InjectMocks
    ClienteService service;

    @Test
    void criaClienteComSucesso() {
        when(repository.existsByEmailIgnoreCase("ana@x.com")).thenReturn(false);
        when(repository.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));

        var resposta = service.criar(new ClienteRequest("Ana", "ana@x.com"));

        assertThat(resposta.nome()).isEqualTo("Ana");
        assertThat(resposta.ativo()).isTrue();
    }

    @Test
    void rejeitaEmailDuplicado() {
        when(repository.existsByEmailIgnoreCase("ana@x.com")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(new ClienteRequest("Ana", "ana@x.com")))
                .isInstanceOf(EmailJaCadastradoException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Deve listar clientes filtrando por e-mail com trim e ignorando maiúsculas/minúsculas")
    void deveListarClientesFiltrandoPorEmailComTrim() {
        Pageable pageable = PageRequest.of(0, 10);
        Cliente cliente = new Cliente("Cliente Exemplo", "cliente@exemplo.com");
        Page<Cliente> page = new PageImpl<>(List.of(cliente), pageable, 1);

        when(repository.findByEmailIgnoreCase("cliente@exemplo.com", pageable)).thenReturn(page);

        Page<ClienteResponse> resultado = service.listar(null, "  cliente@exemplo.com  ", pageable);

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().getFirst().email()).isEqualTo("cliente@exemplo.com");
        verify(repository).findByEmailIgnoreCase("cliente@exemplo.com", pageable);
        verify(repository, never()).findAll(any(Pageable.class));
        verify(repository, never()).findByNomeContainingIgnoreCase(any(), any());
    }

    @Test
    @DisplayName("Deve priorizar filtro por e-mail quando nome e e-mail forem informados")
    void devePriorizarFiltroPorEmailQuandoNomeEEmailForemInformados() {
        Pageable pageable = PageRequest.of(0, 10);
        Cliente cliente = new Cliente("Cliente Exemplo", "cliente@exemplo.com");
        Page<Cliente> page = new PageImpl<>(List.of(cliente), pageable, 1);

        when(repository.findByEmailIgnoreCase("cliente@exemplo.com", pageable)).thenReturn(page);

        Page<ClienteResponse> resultado = service.listar("Outro Nome", "cliente@exemplo.com", pageable);

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().getFirst().email()).isEqualTo("cliente@exemplo.com");
        verify(repository).findByEmailIgnoreCase("cliente@exemplo.com", pageable);
        verify(repository, never()).findByNomeContainingIgnoreCase(any(), any());
    }

    @Test
    @DisplayName("Deve retornar página vazia quando e-mail não for encontrado")
    void deveRetornarPaginaVaziaQuandoEmailNaoForEncontrado() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Cliente> pageVazia = new PageImpl<>(List.of(), pageable, 0);

        when(repository.findByEmailIgnoreCase("inexistente@exemplo.com", pageable)).thenReturn(pageVazia);

        Page<ClienteResponse> resultado = service.listar(null, "inexistente@exemplo.com", pageable);

        assertThat(resultado.isEmpty()).isTrue();
        verify(repository).findByEmailIgnoreCase("inexistente@exemplo.com", pageable);
    }

    @Test
    @DisplayName("Deve listar por nome quando e-mail for nulo ou em branco")
    void deveListarPorNomeQuandoEmailNuloOuEmBranco() {
        Pageable pageable = PageRequest.of(0, 10);
        Cliente cliente = new Cliente("Ana Silva", "ana@x.com");
        Page<Cliente> page = new PageImpl<>(List.of(cliente), pageable, 1);

        when(repository.findByNomeContainingIgnoreCase("Ana", pageable)).thenReturn(page);

        Page<ClienteResponse> resultado = service.listar("Ana", "   ", pageable);

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().getFirst().nome()).isEqualTo("Ana Silva");
        verify(repository).findByNomeContainingIgnoreCase("Ana", pageable);
        verify(repository, never()).findByEmailIgnoreCase(any(), any());
    }

    @Test
    @DisplayName("Deve listar todos os clientes quando nome e e-mail forem nulos ou em branco")
    void deveListarTodosQuandoFiltrosForemNulosOuEmBranco() {
        Pageable pageable = PageRequest.of(0, 10);
        Cliente cliente = new Cliente("Ana Silva", "ana@x.com");
        Page<Cliente> page = new PageImpl<>(List.of(cliente), pageable, 1);

        when(repository.findAll(pageable)).thenReturn(page);

        Page<ClienteResponse> resultado = service.listar(null, null, pageable);

        assertThat(resultado.getContent()).hasSize(1);
        verify(repository).findAll(pageable);
    }

    @Test
    @DisplayName("Deve reativar cliente inativo com sucesso de forma idempotente (AC-1)")
    void deveReativarClienteInativoComSucesso() {
        Cliente cliente = new Cliente("Ana", "ana@x.com");
        cliente.inativar();
        assertThat(cliente.isAtivo()).isFalse();

        when(repository.findById(123L)).thenReturn(Optional.of(cliente));

        ClienteResponse response = service.reativar(123L);

        assertThat(response.ativo()).isTrue();
        assertThat(cliente.isAtivo()).isTrue();
        verify(repository).findById(123L);
    }

    @Test
    @DisplayName("Deve reativar cliente já ativo mantendo o status ativo de forma idempotente (AC-2)")
    void deveReativarClienteJaAtivoDeFormaIdempotente() {
        Cliente cliente = new Cliente("Ana", "ana@x.com");
        assertThat(cliente.isAtivo()).isTrue();

        when(repository.findById(123L)).thenReturn(Optional.of(cliente));

        ClienteResponse response = service.reativar(123L);

        assertThat(response.ativo()).isTrue();
        assertThat(cliente.isAtivo()).isTrue();
        verify(repository).findById(123L);
    }

    @Test
    @DisplayName("Deve lançar ClienteNaoEncontradoException ao tentar reativar cliente inexistente (AC-3)")
    void deveLancarExcecaoAoReativarClienteInexistente() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reativar(999L))
                .isInstanceOf(ClienteNaoEncontradoException.class)
                .hasMessage("Cliente 999 não encontrado");

        verify(repository).findById(999L);
    }

    @Test
    @DisplayName("Deve listar clientes filtrando exclusivamente por ativo=true (AC-4)")
    void deveListarClientesFiltrandoPorAtivoTrue() {
        Pageable pageable = PageRequest.of(0, 10);
        Cliente cliente = new Cliente("Ana", "ana@x.com");
        Page<Cliente> page = new PageImpl<>(List.of(cliente), pageable, 1);

        when(repository.findByAtivo(true, pageable)).thenReturn(page);

        Page<ClienteResponse> resultado = service.listar(null, null, true, pageable);

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().getFirst().ativo()).isTrue();
        verify(repository).findByAtivo(true, pageable);
        verify(repository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("Deve listar clientes filtrando exclusivamente por ativo=false (AC-5)")
    void deveListarClientesFiltrandoPorAtivoFalse() {
        Pageable pageable = PageRequest.of(0, 10);
        Cliente cliente = new Cliente("Ana", "ana@x.com");
        cliente.inativar();
        Page<Cliente> page = new PageImpl<>(List.of(cliente), pageable, 1);

        when(repository.findByAtivo(false, pageable)).thenReturn(page);

        Page<ClienteResponse> resultado = service.listar(null, null, false, pageable);

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getContent().getFirst().ativo()).isFalse();
        verify(repository).findByAtivo(false, pageable);
        verify(repository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("Deve manter comportamento legado ao listar sem informar o filtro ativo (AC-6)")
    void deveManterComportamentoLegadoAoListarSemFiltroAtivo() {
        Pageable pageable = PageRequest.of(0, 10);
        Cliente cliente = new Cliente("Ana", "ana@x.com");
        Page<Cliente> page = new PageImpl<>(List.of(cliente), pageable, 1);

        when(repository.findAll(pageable)).thenReturn(page);

        Page<ClienteResponse> resultado = service.listar(null, null, null, pageable);

        assertThat(resultado.getContent()).hasSize(1);
        verify(repository).findAll(pageable);
        verify(repository, never()).findByAtivo(any(), any());
    }

    @Test
    @DisplayName("Deve listar clientes filtrando por nome e ativo")
    void deveListarClientesFiltrandoPorNomeEAtivo() {
        Pageable pageable = PageRequest.of(0, 10);
        Cliente cliente = new Cliente("Ana Silva", "ana@x.com");
        Page<Cliente> page = new PageImpl<>(List.of(cliente), pageable, 1);

        when(repository.findByNomeContainingIgnoreCaseAndAtivo("Ana", true, pageable)).thenReturn(page);

        Page<ClienteResponse> resultado = service.listar("Ana", null, true, pageable);

        assertThat(resultado.getContent()).hasSize(1);
        verify(repository).findByNomeContainingIgnoreCaseAndAtivo("Ana", true, pageable);
    }

    @Test
    @DisplayName("Deve listar clientes filtrando por e-mail e ativo com trim")
    void deveListarClientesFiltrandoPorEmailEAtivo() {
        Pageable pageable = PageRequest.of(0, 10);
        Cliente cliente = new Cliente("Ana Silva", "ana@x.com");
        Page<Cliente> page = new PageImpl<>(List.of(cliente), pageable, 1);

        when(repository.findByEmailIgnoreCaseAndAtivo("ana@x.com", true, pageable)).thenReturn(page);

        Page<ClienteResponse> resultado = service.listar(null, "  ana@x.com  ", true, pageable);

        assertThat(resultado.getContent()).hasSize(1);
        verify(repository).findByEmailIgnoreCaseAndAtivo("ana@x.com", true, pageable);
    }

    @Test
    @DisplayName("Deve contar todos os clientes quando o parâmetro ativo for nulo (AC-1)")
    void deveContarTodosOsClientesQuandoAtivoForNulo() {
        when(repository.count()).thenReturn(8L);

        ClienteContagemResponse resposta = service.contar(null);

        assertThat(resposta.total()).isEqualTo(8L);
        verify(repository).count();
        verify(repository, never()).countByAtivo(any());
    }

    @Test
    @DisplayName("Deve contar clientes filtrando por ativo verdadeiro (AC-2)")
    void deveContarClientesAtivosQuandoAtivoForTrue() {
        when(repository.countByAtivo(true)).thenReturn(5L);

        ClienteContagemResponse resposta = service.contar(true);

        assertThat(resposta.total()).isEqualTo(5L);
        verify(repository).countByAtivo(true);
        verify(repository, never()).count();
    }

    @Test
    @DisplayName("Deve contar clientes filtrando por ativo falso (AC-3)")
    void deveContarClientesInativosQuandoAtivoForFalse() {
        when(repository.countByAtivo(false)).thenReturn(3L);

        ClienteContagemResponse resposta = service.contar(false);

        assertThat(resposta.total()).isEqualTo(3L);
        verify(repository).countByAtivo(false);
        verify(repository, never()).count();
    }

    @Test
    @DisplayName("Deve retornar total zero na contagem quando base estiver vazia (AC-4)")
    void deveRetornarZeroNaContagemQuandoBaseEstiverVazia() {
        when(repository.count()).thenReturn(0L);

        ClienteContagemResponse resposta = service.contar(null);

        assertThat(resposta.total()).isZero();
        verify(repository).count();
        verify(repository, never()).countByAtivo(any());
    }

    @Test
    @DisplayName("Deve retornar existe=true quando o e-mail estiver cadastrado (AC-1)")
    void deveRetornarTrueQuandoEmailExiste() {
        when(repository.existsByEmailIgnoreCase("maria@exemplo.com")).thenReturn(true);

        ClienteEmailExisteResponse resposta = service.emailExiste("maria@exemplo.com");

        assertThat(resposta.existe()).isTrue();
        verify(repository).existsByEmailIgnoreCase("maria@exemplo.com");
    }

    @Test
    @DisplayName("Deve retornar existe=false quando o e-mail não estiver cadastrado (AC-2)")
    void deveRetornarFalseQuandoEmailNaoExiste() {
        when(repository.existsByEmailIgnoreCase("naoexiste@exemplo.com")).thenReturn(false);

        ClienteEmailExisteResponse resposta = service.emailExiste("naoexiste@exemplo.com");

        assertThat(resposta.existe()).isFalse();
        verify(repository).existsByEmailIgnoreCase("naoexiste@exemplo.com");
    }

    @Test
    @DisplayName("Deve delegar ao repository com e-mail de caixa diferente e retornar true (AC-3)")
    void deveRetornarTrueParaEmailComCaixaDiferente() {
        when(repository.existsByEmailIgnoreCase("MARIA@Exemplo.COM")).thenReturn(true);

        ClienteEmailExisteResponse resposta = service.emailExiste("MARIA@Exemplo.COM");

        assertThat(resposta.existe()).isTrue();
        verify(repository).existsByEmailIgnoreCase("MARIA@Exemplo.COM");
    }

    @Test
    @DisplayName("Deve aplicar trim no e-mail antes de consultar o repository")
    void deveAplicarTrimNoEmailAntesDeConsultarRepository() {
        when(repository.existsByEmailIgnoreCase("maria@exemplo.com")).thenReturn(true);

        ClienteEmailExisteResponse resposta = service.emailExiste("   maria@exemplo.com  ");

        assertThat(resposta.existe()).isTrue();
        verify(repository).existsByEmailIgnoreCase("maria@exemplo.com");
        verify(repository, never()).existsByEmailIgnoreCase("   maria@exemplo.com  ");
    }

    @Test
    @DisplayName("Deve atualizar e-mail com sucesso preservando nome, ativo e criadoEm (AC-1)")
    void deveAtualizarEmailComSucessoPreservandoDemaisCampos() {
        Cliente cliente = new Cliente("Maria Silva", "maria@exemplo.com");
        Instant criadoEmOriginal = cliente.getCriadoEm();

        when(repository.findById(1L)).thenReturn(Optional.of(cliente));
        when(repository.existsByEmailIgnoreCase("maria.nova@exemplo.com")).thenReturn(false);

        ClienteResponse resposta = service.atualizarEmail(1L, new ClienteEmailRequest("maria.nova@exemplo.com"));

        assertThat(resposta.email()).isEqualTo("maria.nova@exemplo.com");
        assertThat(resposta.nome()).isEqualTo("Maria Silva");
        assertThat(resposta.ativo()).isTrue();
        assertThat(resposta.criadoEm()).isEqualTo(criadoEmOriginal);
        assertThat(cliente.getEmail()).isEqualTo("maria.nova@exemplo.com");
        assertThat(cliente.getNome()).isEqualTo("Maria Silva");
        assertThat(cliente.isAtivo()).isTrue();
        assertThat(cliente.getCriadoEm()).isEqualTo(criadoEmOriginal);
        verify(repository).findById(1L);
        verify(repository).existsByEmailIgnoreCase("maria.nova@exemplo.com");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Deve remover espaços das extremidades antes da checagem de duplicidade e persistência (AC-2)")
    void deveRemoverEspacosDoEmailAntesDaChecagemDeDuplicidadeEPersistencia() {
        Cliente cliente = new Cliente("Maria Silva", "maria@exemplo.com");
        Instant criadoEmOriginal = cliente.getCriadoEm();

        when(repository.findById(1L)).thenReturn(Optional.of(cliente));
        when(repository.existsByEmailIgnoreCase("nova.com.espacos@exemplo.com")).thenReturn(false);

        ClienteResponse resposta = service.atualizarEmail(
                1L, new ClienteEmailRequest("  nova.com.espacos@exemplo.com  "));

        assertThat(resposta.email()).isEqualTo("nova.com.espacos@exemplo.com");
        assertThat(resposta.nome()).isEqualTo("Maria Silva");
        assertThat(resposta.ativo()).isTrue();
        assertThat(resposta.criadoEm()).isEqualTo(criadoEmOriginal);
        assertThat(cliente.getEmail()).isEqualTo("nova.com.espacos@exemplo.com");
        verify(repository).findById(1L);
        verify(repository).existsByEmailIgnoreCase("nova.com.espacos@exemplo.com");
        verify(repository, never()).existsByEmailIgnoreCase("  nova.com.espacos@exemplo.com  ");
    }

    @Test
    @DisplayName("Deve tratar atualização com o mesmo e-mail em caixa diferente de forma idempotente (AC-3)")
    void deveTratarIdempotenciaCaseInsensitiveParaProprioCliente() {
        Cliente cliente = new Cliente("Maria Silva", "maria@exemplo.com");
        Instant criadoEmOriginal = cliente.getCriadoEm();

        when(repository.findById(1L)).thenReturn(Optional.of(cliente));

        ClienteResponse resposta = service.atualizarEmail(1L, new ClienteEmailRequest("MARIA@EXEMPLO.COM"));

        assertThat(resposta.email()).isEqualTo("MARIA@EXEMPLO.COM");
        assertThat(resposta.nome()).isEqualTo("Maria Silva");
        assertThat(resposta.ativo()).isTrue();
        assertThat(resposta.criadoEm()).isEqualTo(criadoEmOriginal);
        assertThat(cliente.getEmail()).isEqualTo("MARIA@EXEMPLO.COM");
        verify(repository).findById(1L);
        verify(repository, never()).existsByEmailIgnoreCase(any());
    }

    @Test
    @DisplayName("Deve aplicar trim antes da checagem de idempotência case-insensitive para o próprio cliente")
    void deveTratarIdempotenciaCaseInsensitiveComEspacosAoRedor() {
        Cliente cliente = new Cliente("Maria Silva", "maria@exemplo.com");
        Instant criadoEmOriginal = cliente.getCriadoEm();

        when(repository.findById(1L)).thenReturn(Optional.of(cliente));

        ClienteResponse resposta = service.atualizarEmail(1L, new ClienteEmailRequest("  MARIA@EXEMPLO.COM  "));

        assertThat(resposta.email()).isEqualTo("MARIA@EXEMPLO.COM");
        assertThat(resposta.nome()).isEqualTo("Maria Silva");
        assertThat(resposta.ativo()).isTrue();
        assertThat(resposta.criadoEm()).isEqualTo(criadoEmOriginal);
        assertThat(cliente.getEmail()).isEqualTo("MARIA@EXEMPLO.COM");
        verify(repository).findById(1L);
        verify(repository, never()).existsByEmailIgnoreCase(any());
    }

    @Test
    @DisplayName("Deve permitir troca de e-mail para cliente inativo mantendo ativo igual a false (AC-4)")
    void devePermitirTrocaDeEmailParaClienteInativoMantendoAtivoFalse() {
        Cliente cliente = new Cliente("João Souza", "joao@exemplo.com");
        cliente.inativar();
        Instant criadoEmOriginal = cliente.getCriadoEm();
        assertThat(cliente.isAtivo()).isFalse();

        when(repository.findById(2L)).thenReturn(Optional.of(cliente));
        when(repository.existsByEmailIgnoreCase("joao.atualizado@exemplo.com")).thenReturn(false);

        ClienteResponse resposta = service.atualizarEmail(2L, new ClienteEmailRequest("joao.atualizado@exemplo.com"));

        assertThat(resposta.email()).isEqualTo("joao.atualizado@exemplo.com");
        assertThat(resposta.nome()).isEqualTo("João Souza");
        assertThat(resposta.ativo()).isFalse();
        assertThat(resposta.criadoEm()).isEqualTo(criadoEmOriginal);
        assertThat(cliente.getEmail()).isEqualTo("joao.atualizado@exemplo.com");
        assertThat(cliente.getNome()).isEqualTo("João Souza");
        assertThat(cliente.isAtivo()).isFalse();
        assertThat(cliente.getCriadoEm()).isEqualTo(criadoEmOriginal);
        verify(repository).findById(2L);
        verify(repository).existsByEmailIgnoreCase("joao.atualizado@exemplo.com");
    }

    @Test
    @DisplayName("Deve lançar EmailJaCadastradoException quando e-mail já estiver em uso por outro cliente (AC-5)")
    void deveRejeitarAtualizacaoQuandoEmailPertencerAOutroClienteCaseInsensitive() {
        Cliente cliente = new Cliente("Maria Silva", "maria@exemplo.com");
        Instant criadoEmOriginal = cliente.getCriadoEm();

        when(repository.findById(1L)).thenReturn(Optional.of(cliente));
        when(repository.existsByEmailIgnoreCase("outro.cliente@exemplo.com")).thenReturn(true);

        assertThatThrownBy(() -> service.atualizarEmail(1L, new ClienteEmailRequest("outro.cliente@exemplo.com")))
                .isInstanceOf(EmailJaCadastradoException.class)
                .hasMessage("E-mail outro.cliente@exemplo.com já cadastrado");

        assertThat(cliente.getEmail()).isEqualTo("maria@exemplo.com");
        assertThat(cliente.getNome()).isEqualTo("Maria Silva");
        assertThat(cliente.isAtivo()).isTrue();
        assertThat(cliente.getCriadoEm()).isEqualTo(criadoEmOriginal);
        verify(repository).findById(1L);
        verify(repository).existsByEmailIgnoreCase("outro.cliente@exemplo.com");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Deve lançar ClienteNaoEncontradoException precedendo checagem de conflito quando cliente não existir (AC-6)")
    void deveLancarExcecaoQuandoClienteNaoExistirPrecedendoConflito() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizarEmail(999L, new ClienteEmailRequest("qualquer@exemplo.com")))
                .isInstanceOf(ClienteNaoEncontradoException.class)
                .hasMessage("Cliente 999 não encontrado");

        verify(repository).findById(999L);
        verify(repository, never()).existsByEmailIgnoreCase(any());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Deve persistir atualização de e-mail válido com exatamente 160 caracteres (AC-10)")
    void deveAtualizarEmailValidoCom160Caracteres() {
        String email160 = "a".repeat(64) + "@" + "b".repeat(45) + "." + "c".repeat(45) + ".com";
        assertThat(email160).hasSize(160);

        Cliente cliente = new Cliente("Maria Silva", "maria@exemplo.com");
        Instant criadoEmOriginal = cliente.getCriadoEm();

        when(repository.findById(1L)).thenReturn(Optional.of(cliente));
        when(repository.existsByEmailIgnoreCase(email160)).thenReturn(false);

        ClienteResponse resposta = service.atualizarEmail(1L, new ClienteEmailRequest(email160));

        assertThat(resposta.email()).isEqualTo(email160);
        assertThat(resposta.nome()).isEqualTo("Maria Silva");
        assertThat(resposta.ativo()).isTrue();
        assertThat(resposta.criadoEm()).isEqualTo(criadoEmOriginal);
        assertThat(cliente.getEmail()).isEqualTo(email160);
        verify(repository).findById(1L);
        verify(repository).existsByEmailIgnoreCase(email160);
    }

    @Test
    @DisplayName("Deve inativar em lote todos os clientes ativos com sucesso preservando a ordem (AC-1)")
    void deveInativarEmLoteClientesAtivosComSucesso() {
        Cliente cliente1 = criarCliente(1L, "Ana", "ana@x.com");
        Cliente cliente2 = criarCliente(2L, "Bruno", "bruno@x.com");
        Cliente cliente3 = criarCliente(3L, "Carla", "carla@x.com");

        when(repository.findAllById(List.of(1L, 2L, 3L))).thenReturn(List.of(cliente1, cliente2, cliente3));

        ClienteInativacaoLoteResponse resposta = service.inativarEmLote(
                new ClienteInativacaoLoteRequest(List.of(1L, 2L, 3L)));

        assertThat(resposta.totalProcessados()).isEqualTo(3);
        assertThat(resposta.idsInativados()).containsExactly(1L, 2L, 3L);
        assertThat(cliente1.isAtivo()).isFalse();
        assertThat(cliente2.isAtivo()).isFalse();
        assertThat(cliente3.isAtivo()).isFalse();
        verify(repository).findAllById(List.of(1L, 2L, 3L));
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("Deve inativar lote misto de forma idempotente mantendo cliente já inativo e retornando todos os IDs (AC-2)")
    void deveInativarEmLoteMistoDeFormaIdempotente() {
        Cliente clienteAtivo = criarCliente(1L, "Ana", "ana@x.com");
        Cliente clienteJaInativo = criarCliente(2L, "Bruno", "bruno@x.com");
        clienteJaInativo.inativar();
        assertThat(clienteAtivo.isAtivo()).isTrue();
        assertThat(clienteJaInativo.isAtivo()).isFalse();

        when(repository.findAllById(List.of(1L, 2L))).thenReturn(List.of(clienteAtivo, clienteJaInativo));

        ClienteInativacaoLoteResponse resposta = service.inativarEmLote(
                new ClienteInativacaoLoteRequest(List.of(1L, 2L)));

        assertThat(resposta.totalProcessados()).isEqualTo(2);
        assertThat(resposta.idsInativados()).containsExactly(1L, 2L);
        assertThat(clienteAtivo.isAtivo()).isFalse();
        assertThat(clienteJaInativo.isAtivo()).isFalse();
        verify(repository).findAllById(List.of(1L, 2L));
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("Deve desduplicar IDs preservando a ordem da primeira ocorrência e processar cada cliente uma única vez (AC-3)")
    void deveDesduplicarIdsPreservandoOrdemEProcessarCadaClienteUmaUnicaVez() {
        Cliente cliente1 = criarClienteSpy(1L, "Ana", "ana@x.com");
        Cliente cliente2 = criarClienteSpy(2L, "Bruno", "bruno@x.com");

        when(repository.findAllById(List.of(1L, 2L))).thenReturn(List.of(cliente1, cliente2));

        ClienteInativacaoLoteResponse resposta = service.inativarEmLote(
                new ClienteInativacaoLoteRequest(List.of(1L, 2L, 1L, 2L)));

        assertThat(resposta.totalProcessados()).isEqualTo(2);
        assertThat(resposta.idsInativados()).containsExactly(1L, 2L);
        assertThat(cliente1.isAtivo()).isFalse();
        assertThat(cliente2.isAtivo()).isFalse();
        verify(cliente1, times(1)).inativar();
        verify(cliente2, times(1)).inativar();
        verify(repository, times(1)).findAllById(List.of(1L, 2L));
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("Deve preservar ordem de primeira ocorrência na desduplicação mesmo quando o repositório retorna em outra ordem (AC-3)")
    void devePreservarOrdemDePrimeiraOcorrenciaNaDesduplicacao() {
        Cliente cliente1 = criarClienteSpy(1L, "Ana", "ana@x.com");
        Cliente cliente2 = criarClienteSpy(2L, "Bruno", "bruno@x.com");
        Cliente cliente3 = criarClienteSpy(3L, "Carla", "carla@x.com");

        when(repository.findAllById(List.of(3L, 1L, 2L))).thenReturn(List.of(cliente1, cliente2, cliente3));

        ClienteInativacaoLoteResponse resposta = service.inativarEmLote(
                new ClienteInativacaoLoteRequest(List.of(3L, 1L, 3L, 2L, 1L)));

        assertThat(resposta.totalProcessados()).isEqualTo(3);
        assertThat(resposta.idsInativados()).containsExactly(3L, 1L, 2L);
        assertThat(cliente1.isAtivo()).isFalse();
        assertThat(cliente2.isAtivo()).isFalse();
        assertThat(cliente3.isAtivo()).isFalse();
        verify(cliente1, times(1)).inativar();
        verify(cliente2, times(1)).inativar();
        verify(cliente3, times(1)).inativar();
        verify(repository, times(1)).findAllById(List.of(3L, 1L, 2L));
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("Deve abortar antes de qualquer alteração e lançar ClienteNaoEncontradoException quando ao menos um ID não existir (AC-4)")
    void deveAbortarSemAlterarClientesQuandoAlgumIdNaoExistir() {
        Cliente cliente1 = criarClienteSpy(1L, "Ana", "ana@x.com");
        Cliente cliente2 = criarClienteSpy(2L, "Bruno", "bruno@x.com");

        when(repository.findAllById(List.of(1L, 999L, 2L))).thenReturn(List.of(cliente1, cliente2));

        assertThatThrownBy(() -> service.inativarEmLote(
                new ClienteInativacaoLoteRequest(List.of(1L, 999L, 2L))))
                .isInstanceOf(ClienteNaoEncontradoException.class)
                .hasMessage("Cliente 999 não encontrado");

        assertThat(cliente1.isAtivo()).isTrue();
        assertThat(cliente2.isAtivo()).isTrue();
        verify(cliente1, never()).inativar();
        verify(cliente2, never()).inativar();
        verify(repository).findAllById(List.of(1L, 999L, 2L));
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("Deve inativar em lote exatamente 100 clientes distintos no limite máximo permitido (AC-8)")
    void deveInativarEmLoteNoLimiteMaximoDe100IdsDistintos() {
        List<Long> ids = LongStream.rangeClosed(1L, 100L).boxed().toList();
        List<Cliente> clientes = ids.stream()
                .map(id -> criarCliente(id, "Cliente " + id, "cliente" + id + "@exemplo.com"))
                .toList();

        when(repository.findAllById(ids)).thenReturn(clientes);

        ClienteInativacaoLoteResponse resposta = service.inativarEmLote(new ClienteInativacaoLoteRequest(ids));

        assertThat(resposta.totalProcessados()).isEqualTo(100);
        assertThat(resposta.idsInativados()).hasSize(100).containsExactlyElementsOf(ids);
        assertThat(clientes).allSatisfy(cliente -> assertThat(cliente.isAtivo()).isFalse());
        verify(repository).findAllById(ids);
        verifyNoMoreInteractions(repository);
    }

    private static Cliente criarCliente(Long id, String nome, String email) {
        return criarCliente(id, nome, email, false);
    }

    private static Cliente criarClienteSpy(Long id, String nome, String email) {
        return criarCliente(id, nome, email, true);
    }

    private static Cliente criarCliente(Long id, String nome, String email, boolean comSpy) {
        Cliente instancia = new Cliente(nome, email);
        Cliente cliente = comSpy ? spy(instancia) : instancia;
        ReflectionTestUtils.setField(cliente, "id", id);
        return cliente;
    }
}
