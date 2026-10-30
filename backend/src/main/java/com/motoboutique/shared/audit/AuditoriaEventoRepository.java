package com.motoboutique.shared.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.time.Instant;

public interface AuditoriaEventoRepository extends JpaRepository<AuditoriaEvento, Long> {
    List<AuditoriaEvento> findTop10ByEliminadoFalseOrderByFechaDescIdDesc();

    @Query("""
            select a from AuditoriaEvento a
            where a.eliminado = false
              and (cast(:q as String) is null
                   or lower(a.actor) like lower(concat('%', cast(:q as String), '%'))
                   or lower(a.accion) like lower(concat('%', cast(:q as String), '%'))
                   or lower(a.entidad) like lower(concat('%', cast(:q as String), '%')))
              and (cast(:usuario as String) is null
                   or lower(a.actor) like lower(concat('%', cast(:usuario as String), '%')))
              and (cast(:accion as String) is null
                   or lower(a.accion) like lower(concat('%', cast(:accion as String), '%')))
              and a.fecha >= :desde
              and a.fecha <= :hasta
            """)
    Page<AuditoriaEvento> buscar(@Param("q") String q, @Param("usuario") String usuario,
            @Param("accion") String accion, @Param("desde") Instant desde,
            @Param("hasta") Instant hasta, Pageable pageable);
}
