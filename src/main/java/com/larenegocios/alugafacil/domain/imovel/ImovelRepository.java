package com.larenegocios.alugafacil.domain.imovel;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImovelRepository extends JpaRepository<Imovel, Long> {

    Page<Imovel> findByDisponivelTrue(Pageable pageable);

    Page<Imovel> findByCidadeIgnoreCaseAndDisponivelTrue(String cidade, Pageable pageable);

    Page<Imovel> findByLocadorId(Long locadorId, Pageable pageable);
}
