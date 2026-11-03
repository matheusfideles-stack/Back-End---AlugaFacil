package com.larenegocios.alugafacil.domain.proposta;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PropostaRepository extends JpaRepository<Proposta, Long> {

    Page<Proposta> findByLocatarioId(Long locatarioId, Pageable pageable);

    Page<Proposta> findByImovelLocadorId(Long locadorId, Pageable pageable);

    boolean existsByImovelIdAndLocatarioIdAndStatus(Long imovelId, Long locatarioId, StatusProposta status);

    List<Proposta> findByImovelIdAndStatus(Long imovelId, StatusProposta status);
}
