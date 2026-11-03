package com.larenegocios.alugafacil.domain.proposta;

import com.larenegocios.alugafacil.domain.contrato.Contrato;
import com.larenegocios.alugafacil.domain.imovel.Imovel;
import com.larenegocios.alugafacil.domain.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Pedido de aluguel: o contrato so nasce quando o proprietario aceita.
@Entity
@Table(name = "propostas")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Proposta {

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

    @Column(length = 500)
    private String mensagem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusProposta status = StatusProposta.PENDENTE;

    @Column(name = "motivo_recusa", length = 300)
    private String motivoRecusa;

    // Preenchido quando o proprietario aceita: e o contrato que nasceu desta proposta.
    @ManyToOne
    @JoinColumn(name = "contrato_id")
    private Contrato contrato;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "respondido_em")
    private LocalDateTime respondidoEm;
}
