package com.example.clientes.service;

import com.example.clientes.domain.Cliente;
import com.example.clientes.dto.ClienteContagemResponse;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
}
