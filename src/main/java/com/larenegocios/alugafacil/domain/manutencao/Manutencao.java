package com.larenegocios.alugafacil.domain.manutencao;

import com.larenegocios.alugafacil.domain.contrato.Contrato;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "manutencoes")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Manutencao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "contrato_id")
    private Contrato contrato;

    @Column(nullable = false, length = 50)
    private String tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UrgenciaManutencao urgencia = UrgenciaManutencao.NORMAL;

    @Column(nullable = false, length = 1000)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusManutencao status = StatusManutencao.ABERTA;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public String getProtocolo() {
        return "MAN-" + String.format("%06d", id);
    }
}
