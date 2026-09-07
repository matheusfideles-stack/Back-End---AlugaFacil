package com.larenegocios.alugafacil.domain.contrato;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContratoRepository extends JpaRepository<Contrato, Long> {

    Page<Contrato> findByLocatarioId(Long locatarioId, Pageable pageable);

    Page<Contrato> findByImovelLocadorId(Long locadorId, Pageable pageable);

    boolean existsByImovelIdAndStatus(Long imovelId, StatusContrato status);
}
