package com.larenegocios.alugafacil.domain.pagamento;

import com.larenegocios.alugafacil.domain.contrato.Contrato;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pagamentos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "contrato_id")
    private Contrato contrato;

    @Column(name = "referencia_mes", nullable = false)
    private String referenciaMes;

    @Column(nullable = false)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MetodoPagamento metodo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPagamento status = StatusPagamento.PENDENTE;

    @Column(name = "id_transacao_gateway")
    private String idTransacaoGateway;

    @Column(name = "status_detalhe", length = 100)
    private String statusDetalhe;

    @Column(name = "link_pagamento", length = 500)
    private String linkPagamento;

    @Column(name = "pix_copia_e_cola", columnDefinition = "TEXT")
    private String pixCopiaECola;

    @Column(name = "linha_digitavel", length = 100)
    private String linhaDigitavel;

    @Column(name = "expira_em")
    private LocalDateTime expiraEm;

    @Column(nullable = false)
    private LocalDate vencimento;

    @Column(name = "pago_em")
    private LocalDateTime pagoEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    // Nao persistido: ocupa dezenas de KB e pode ser regerado a partir do pixCopiaECola.
    @Transient
    private String pixQrCodeBase64;

    @Transient
    private String urlAutenticacao3ds;
}
