package com.larenegocios.alugafacil.domain.contrato;

import com.larenegocios.alugafacil.domain.imovel.Imovel;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "contratos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Contrato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "imovel_id")
    private Imovel imovel;

    @ManyToOne(optional = false)
    @JoinColumn(name = "locatario_id")
    private Usuario locatario;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim", nullable = false)
    private LocalDate dataFim;

    @Column(name = "dia_vencimento", nullable = false)
    private Integer diaVencimento;

    @Column(name = "valor_mensal", nullable = false)
    private BigDecimal valorMensal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusContrato status = StatusContrato.ATIVO;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();
}
