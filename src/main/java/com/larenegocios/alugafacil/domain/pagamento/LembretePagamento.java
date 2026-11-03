package com.larenegocios.alugafacil.domain.pagamento;

import com.larenegocios.alugafacil.domain.contrato.Contrato;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Registro de que o aviso daquele estagio ja foi mandado: e ele que impede e-mail repetido quando o servidor
// reinicia ou a rotina roda duas vezes no mesmo dia.
@Entity
@Table(name = "lembretes_pagamento",
        uniqueConstraints = @UniqueConstraint(name = "uk_lembrete_contrato_mes_estagio",
                columnNames = {"contrato_id", "referencia_mes", "estagio"}))
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class LembretePagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "contrato_id")
    private Contrato contrato;

    @Column(name = "referencia_mes", nullable = false, length = 7)
    private String referenciaMes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstagioDeLembrete estagio;

    @Column(name = "enviado_em", nullable = false)
    private LocalDateTime enviadoEm = LocalDateTime.now();
}
