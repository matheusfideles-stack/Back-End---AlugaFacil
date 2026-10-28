package com.larenegocios.alugafacil.domain.manutencao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManutencaoRepository extends JpaRepository<Manutencao, Long> {

    Page<Manutencao> findByContratoLocatarioId(Long locatarioId, Pageable pageable);

    Page<Manutencao> findByContratoImovelLocadorId(Long locadorId, Pageable pageable);
}
