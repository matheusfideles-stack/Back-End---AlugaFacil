package com.larenegocios.alugafacil.domain.usuario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TokenRecuperacaoSenhaRepository extends JpaRepository<TokenRecuperacaoSenha, Long> {

    Optional<TokenRecuperacaoSenha> findFirstByUsuarioIdAndUsadoEmIsNullOrderByIdDesc(Long usuarioId);

    boolean existsByTokenHash(String tokenHash);

    @Modifying
    @Query("update TokenRecuperacaoSenha t set t.usadoEm = :agora where t.usuario.id = :usuarioId and t.usadoEm is null")
    void invalidarPendentes(Long usuarioId, LocalDateTime agora);
}
