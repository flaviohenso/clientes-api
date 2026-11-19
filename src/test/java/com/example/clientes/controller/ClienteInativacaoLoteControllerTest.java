package com.example.clientes.controller;

import com.example.clientes.dto.ClienteInativacaoLoteRequest;
import com.example.clientes.dto.ClienteInativacaoLoteResponse;
import com.example.clientes.exception.ClienteNaoEncontradoException;
import com.example.clientes.service.ClienteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClienteController.class)
class ClienteInativacaoLoteControllerTest {

    private static final String ENDPOINT = "/api/v1/clientes/inativar-lote";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClienteService service;

    @Test
    @DisplayName("Deve retornar 200 OK com totalProcessados e idsInativados para lote válido (AC-1)")
    void deveRetornar200ParaLoteValido() throws Exception {
        List<Long> ids = List.of(1L, 2L, 3L);
        ClienteInativacaoLoteRequest request = new ClienteInativacaoLoteRequest(ids);

        when(service.inativarEmLote(request))
                .thenReturn(new ClienteInativacaoLoteResponse(3, ids));

        mockMvc.perform(patch(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\": [1, 2, 3]}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalProcessados").value(3))
                .andExpect(jsonPath("$.idsInativados", contains(1, 2, 3)));

        verify(service).inativarEmLote(request);
        verifyNoMoreInteractions(service);
    }

    @Test
    @DisplayName("Deve retornar 200 OK para exatamente 100 IDs válidos no limite máximo (AC-8)")
    void deveRetornar200Para100IdsValidos() throws Exception {
        List<Long> ids = LongStream.rangeClosed(1L, 100L).boxed().toList();
        Integer[] idsEsperados = IntStream.rangeClosed(1, 100).boxed().toArray(Integer[]::new);
        ClienteInativacaoLoteRequest request = new ClienteInativacaoLoteRequest(ids);

        when(service.inativarEmLote(request))
                .thenReturn(new ClienteInativacaoLoteResponse(100, ids));

        mockMvc.perform(patch(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gerarPayloadIds(ids)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalProcessados").value(100))
                .andExpect(jsonPath("$.idsInativados", hasSize(100)))
                .andExpect(jsonPath("$.idsInativados", contains(idsEsperados)));

        verify(service).inativarEmLote(request);
        verifyNoMoreInteractions(service);
    }

    @Test
    @DisplayName("Deve retornar 404 Not Found no formato ProblemDetail quando cliente não existir (AC-4)")
    void deveRetornar404ProblemDetailQuandoClienteNaoEncontrado() throws Exception {
        List<Long> ids = List.of(1L, 999L, 2L);
        ClienteInativacaoLoteRequest request = new ClienteInativacaoLoteRequest(ids);

        when(service.inativarEmLote(request))
                .thenThrow(new ClienteNaoEncontradoException(999L));

        mockMvc.perform(patch(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\": [1, 999, 2]}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Cliente 999 não encontrado"));

        verify(service).inativarEmLote(request);
        verifyNoMoreInteractions(service);
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request sem acionar service quando campo ids estiver ausente (AC-5)")
    void deveRetornar400QuandoCampoIdsAusente() throws Exception {
        executarPatchEsperandoBadRequest("{}");
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request sem acionar service quando campo ids for nulo (AC-5)")
    void deveRetornar400QuandoCampoIdsNulo() throws Exception {
        executarPatchEsperandoBadRequest("{\"ids\": null}");
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request sem acionar service quando lista ids estiver vazia (AC-5)")
    void deveRetornar400QuandoListaIdsVazia() throws Exception {
        executarPatchEsperandoBadRequest("{\"ids\": []}");
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request sem acionar service quando lista contiver elemento nulo (AC-6)")
    void deveRetornar400QuandoListaContiverElementoNulo() throws Exception {
        executarPatchEsperandoBadRequest("{\"ids\": [1, null]}");
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request sem acionar service para 101 IDs distintos (AC-7)")
    void deveRetornar400QuandoExcederLimiteCom101IdsDistintos() throws Exception {
        List<Long> ids = LongStream.rangeClosed(1L, 101L).boxed().toList();
        executarPatchEsperandoBadRequest(gerarPayloadIds(ids));
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request sem acionar service para 101 IDs mesmo com duplicatas (AC-7)")
    void deveRetornar400QuandoExcederLimiteCom101IdsComDuplicatas() throws Exception {
        List<Long> ids = Collections.nCopies(101, 1L);
        executarPatchEsperandoBadRequest(gerarPayloadIds(ids));
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request sem acionar service para JSON malformado")
    void deveRetornar400ParaJsonMalformado() throws Exception {
        executarPatchEsperandoBadRequest("{\"ids\": [1, 2");
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request sem acionar service quando item da lista tiver tipo inválido")
    void deveRetornar400QuandoItemDaListaTiverTipoInvalido() throws Exception {
        executarPatchEsperandoBadRequest("{\"ids\": [1, \"abc\"]}");
    }

    @Test
    @DisplayName("Deve retornar 400 Bad Request sem acionar service quando campo ids não for um array")
    void deveRetornar400QuandoCampoIdsNaoForArray() throws Exception {
        executarPatchEsperandoBadRequest("{\"ids\": \"1,2,3\"}");
    }

    private void executarPatchEsperandoBadRequest(String payload) throws Exception {
        mockMvc.perform(patch(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(service);
    }

    private static String gerarPayloadIds(List<Long> ids) {
        return ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", ", "{\"ids\": [", "]}"));
    }
}
