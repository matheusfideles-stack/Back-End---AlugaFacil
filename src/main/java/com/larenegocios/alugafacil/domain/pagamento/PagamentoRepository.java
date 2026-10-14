package com.larenegocios.alugafacil.domain.pagamento;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {

    Page<Pagamento> findByContratoId(Long contratoId, Pageable pageable);

    Page<Pagamento> findByContratoLocatarioId(Long locatarioId, Pageable pageable);

    Optional<Pagamento> findByContratoIdAndReferenciaMes(Long contratoId, String referenciaMes);

    Optional<Pagamento> findByIdTransacaoGateway(String idTransacaoGateway);
}
