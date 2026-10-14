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

    // Mes de referencia do aluguel, formato AAAA-MM
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

    // Identificador retornado pelo gateway de pagamento externo - API da forma de pagamento que vamos fazer
    @Column(name = "id_transacao_gateway")
    private String idTransacaoGateway;

    // Detalhe cru do provedor, util para explicar recusas de cartao (ex.: "cc_rejected_bad_filled_security_code")
    @Column(name = "status_detalhe", length = 100)
    private String statusDetalhe;

    // Pagina de checkout do Pix ou PDF do boleto
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

    /**
     * Imagem do QR Code Pix em base64. Nao e persistida: ocupa dezenas de KB e pode ser
     * regerada a partir do {@link #pixCopiaECola} sempre que necessario.
     */
    @Transient
    private String pixQrCodeBase64;

    /** URL do challenge 3DS do cartao de debito. Curta duracao, tambem nao persistida. */
    @Transient
    private String urlAutenticacao3ds;
}
