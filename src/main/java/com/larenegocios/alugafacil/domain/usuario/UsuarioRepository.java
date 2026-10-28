package com.larenegocios.alugafacil.domain.usuario;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<UserDetails> findByEmail(String email);

    Optional<Usuario> findUsuarioByEmail(String email);

    Page<Usuario> findByPerfil(Perfil perfil, Pageable pageable);

    boolean existsByEmailOrCpf(String email, String cpf);
}
