package com.larenegocios.alugafacil.domain.imovel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FotoImovelRepository extends JpaRepository<FotoImovel, Long> {

    List<FotoImovel> findByImovelIdOrderByOrdemAscIdAsc(Long imovelId);

    Optional<FotoImovel> findFirstByImovelIdOrderByOrdemAscIdAsc(Long imovelId);

    Optional<FotoImovel> findByIdAndImovelId(Long id, Long imovelId);

    long countByImovelId(Long imovelId);

    @Query("select coalesce(max(f.ordem), -1) from FotoImovel f where f.imovel.id = :imovelId")
    int maiorOrdem(Long imovelId);

    @Query("select coalesce(min(f.ordem), 0) from FotoImovel f where f.imovel.id = :imovelId")
    int menorOrdem(Long imovelId);
}
