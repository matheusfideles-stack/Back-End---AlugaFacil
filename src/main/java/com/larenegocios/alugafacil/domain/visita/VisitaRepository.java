package com.larenegocios.alugafacil.domain.visita;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitaRepository extends JpaRepository<Visita, Long> {

    Page<Visita> findByLocatarioId(Long locatarioId, Pageable pageable);

    Page<Visita> findByImovelLocadorId(Long locadorId, Pageable pageable);
}
