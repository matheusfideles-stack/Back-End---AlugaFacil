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

    @Column(nullable = false)
    private LocalDate vencimento;

    @Column(name = "pago_em")
    private LocalDateTime pagoEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();
}
