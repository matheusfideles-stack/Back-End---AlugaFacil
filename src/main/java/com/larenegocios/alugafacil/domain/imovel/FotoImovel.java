package com.larenegocios.alugafacil.domain.imovel;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "fotos_imovel")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class FotoImovel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "imovel_id")
    private Imovel imovel;

    @Column(nullable = false, length = 80)
    private String arquivo;

    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    @Column(nullable = false)
    private Long tamanho;

    @Column(nullable = false)
    private Integer ordem;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();
}
