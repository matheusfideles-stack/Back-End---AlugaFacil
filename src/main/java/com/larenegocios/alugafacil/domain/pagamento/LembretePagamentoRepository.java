package com.larenegocios.alugafacil.domain.pagamento;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LembretePagamentoRepository extends JpaRepository<LembretePagamento, Long> {

    boolean existsByContratoIdAndReferenciaMesAndEstagio(Long contratoId, String referenciaMes, EstagioDeLembrete estagio);
}
