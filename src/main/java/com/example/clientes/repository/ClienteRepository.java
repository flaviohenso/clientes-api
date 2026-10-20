package com.example.clientes.repository;

import com.example.clientes.domain.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    boolean existsByEmailIgnoreCase(String email);

    Page<Cliente> findByNomeContainingIgnoreCase(String nome, Pageable pageable);

    Page<Cliente> findByEmailIgnoreCase(String email, Pageable pageable);

    Page<Cliente> findByAtivo(Boolean ativo, Pageable pageable);

    Page<Cliente> findByNomeContainingIgnoreCaseAndAtivo(String nome, Boolean ativo, Pageable pageable);

    Page<Cliente> findByEmailIgnoreCaseAndAtivo(String email, Boolean ativo, Pageable pageable);

    long countByAtivo(Boolean ativo);
}
