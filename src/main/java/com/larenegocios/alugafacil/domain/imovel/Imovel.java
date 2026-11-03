package com.larenegocios.alugafacil.domain.imovel;

import com.larenegocios.alugafacil.domain.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "imoveis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Imovel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "locador_id")
    private Usuario locador;

    @Column(nullable = false)
    private String titulo;

    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoImovel tipo;

    @Column(nullable = false)
    private String logradouro;

    @Column(nullable = false)
    private String numero;

    private String complemento;

    @Column(nullable = false)
    private String bairro;

    @Column(nullable = false)
    private String cidade;

    @Column(nullable = false, length = 2)
    private String uf;

    @Column(nullable = false)
    private String cep;

    @Column(name = "valor_aluguel", nullable = false)
    private BigDecimal valorAluguel;

    @Column(name = "valor_condominio")
    private BigDecimal valorCondominio = BigDecimal.ZERO;

    private Integer quartos;

    private Integer banheiros;

    private Integer vagas;

    @Column(name = "area_m2")
    private Integer areaM2;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "imovel_comodidades", joinColumns = @JoinColumn(name = "imovel_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "comodidade", length = 40)
    @Fetch(FetchMode.SUBSELECT)
    private Set<Comodidade> comodidades = new LinkedHashSet<>();

    // Posicao no mapa, preenchida pelo geocodificador. Nula quando o endereco nao foi encontrado.
    private Double latitude;

    private Double longitude;

    @Column(nullable = false)
    private Boolean disponivel = true;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();
}
