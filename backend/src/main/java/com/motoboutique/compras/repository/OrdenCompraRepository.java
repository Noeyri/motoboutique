package com.motoboutique.compras.repository;

import com.motoboutique.compras.domain.OrdenCompra;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface OrdenCompraRepository extends JpaRepository<OrdenCompra, Long>,
        JpaSpecificationExecutor<OrdenCompra> {
    interface SerieMensual {
        LocalDate getMes();
        java.math.BigDecimal getTotal();
    }

    interface TotalProveedor {
        Long getProveedorId();
        String getProveedor();
        Long getOrdenes();
        java.math.BigDecimal getTotal();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrdenCompra o where o.id = :id and o.eliminado = false")
    Optional<OrdenCompra> bloquearActiva(@Param("id") Long id);

    boolean existsByProveedorIdAndEliminadoFalse(Long proveedorId);

    @Query("select coalesce(sum(o.total), 0) from OrdenCompra o "
            + "where o.eliminado = false and o.estado in ('CONFIRMADA', 'RECIBIDA') "
            + "and o.fecha >= :desde and o.fecha < :hasta")
    java.math.BigDecimal totalComprasEntre(@Param("desde") Instant desde, @Param("hasta") Instant hasta);

    @Query(value = """
            select cast(date_trunc('month', fecha at time zone 'America/Lima') as date) as mes,
                   sum(total) as total
            from ordenes_compra
            where eliminado = false and estado in ('CONFIRMADA', 'RECIBIDA')
              and fecha >= :desde and fecha < :hasta
            group by cast(date_trunc('month', fecha at time zone 'America/Lima') as date)
            order by mes
            """, nativeQuery = true)
    List<SerieMensual> serieMensual(@Param("desde") Instant desde, @Param("hasta") Instant hasta);

    @Query(value = """
            select p.id as "proveedorId", p.razon_social as proveedor,
                   count(o.id) as ordenes, coalesce(sum(o.total), 0) as total
            from proveedores p
            left join ordenes_compra o on o.proveedor_id = p.id and o.eliminado = false
              and o.estado in ('CONFIRMADA', 'RECIBIDA')
              and (cast(:desde as timestamptz) is null or o.fecha >= cast(:desde as timestamptz))
              and (cast(:hasta as timestamptz) is null or o.fecha <= cast(:hasta as timestamptz))
            where p.eliminado = false
              and (:q is null or lower(p.razon_social) like lower(concat('%', :q, '%'))
                   or lower(p.ruc) like lower(concat('%', :q, '%')))
            group by p.id, p.razon_social
            having count(o.id) > 0
            order by total desc, p.razon_social
            limit :limite offset :offset
            """, nativeQuery = true)
    List<TotalProveedor> totalesPorProveedor(@Param("desde") Instant desde, @Param("hasta") Instant hasta,
                                             @Param("q") String q, @Param("limite") int limite,
                                             @Param("offset") long offset);

    @Query(value = """
            select count(*) from (
                select p.id
                from proveedores p
                join ordenes_compra o on o.proveedor_id = p.id and o.eliminado = false
                  and o.estado in ('CONFIRMADA', 'RECIBIDA')
                  and (cast(:desde as timestamptz) is null or o.fecha >= cast(:desde as timestamptz))
                  and (cast(:hasta as timestamptz) is null or o.fecha <= cast(:hasta as timestamptz))
                where p.eliminado = false
                  and (:q is null or lower(p.razon_social) like lower(concat('%', :q, '%'))
                       or lower(p.ruc) like lower(concat('%', :q, '%')))
                group by p.id
            ) proveedores
            """, nativeQuery = true)
    long contarProveedoresParaReporte(@Param("desde") Instant desde,
                                      @Param("hasta") Instant hasta, @Param("q") String q);

    @Query(value = """
            select coalesce(sum(o.total), 0)
            from ordenes_compra o join proveedores p on p.id = o.proveedor_id
            where p.eliminado = false and o.eliminado = false
              and o.estado in ('CONFIRMADA', 'RECIBIDA')
              and (cast(:desde as timestamptz) is null or o.fecha >= cast(:desde as timestamptz))
              and (cast(:hasta as timestamptz) is null or o.fecha <= cast(:hasta as timestamptz))
              and (:q is null or lower(p.razon_social) like lower(concat('%', :q, '%'))
                   or lower(p.ruc) like lower(concat('%', :q, '%')))
            """, nativeQuery = true)
    java.math.BigDecimal totalProveedoresParaReporte(@Param("desde") Instant desde,
                                                     @Param("hasta") Instant hasta,
                                                     @Param("q") String q);
}
