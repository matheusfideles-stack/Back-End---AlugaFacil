package com.larenegocios.alugafacil.domain.avaliacao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, Long> {

    interface ContagemPorNota {
        Integer getNota();

        Long getTotal();
    }

    interface ResumoPorImovel {
        Long getImovelId();

        Double getMedia();

        Long getTotal();
    }

    Page<Avaliacao> findByImovelId(Long imovelId, Pageable pageable);

    Optional<Avaliacao> findByImovelIdAndUsuarioId(Long imovelId, Long usuarioId);

    @Query("select a.nota as nota, count(a) as total from Avaliacao a where a.imovel.id = :imovelId group by a.nota")
    List<ContagemPorNota> contagemPorNota(Long imovelId);

    @Query("select a.imovel.id as imovelId, avg(a.nota) as media, count(a) as total "
            + "from Avaliacao a where a.imovel.id in :imovelIds group by a.imovel.id")
    List<ResumoPorImovel> resumoPorImoveis(Collection<Long> imovelIds);
}
