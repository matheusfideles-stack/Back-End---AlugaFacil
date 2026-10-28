package com.larenegocios.alugafacil.domain.usuario;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tokens_confirmacao_email")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class TokenConfirmacaoEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "usado_em")
    private LocalDateTime usadoEm;

    @Column(name = "tentativas", nullable = false, columnDefinition = "integer default 0")
    private Integer tentativas = 0;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public boolean utilizavel(LocalDateTime agora) {
        return usadoEm == null && expiraEm.isAfter(agora);
    }
}
