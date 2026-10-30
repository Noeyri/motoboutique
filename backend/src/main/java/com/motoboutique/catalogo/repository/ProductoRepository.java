package com.motoboutique.catalogo.repository;

import com.motoboutique.catalogo.domain.Producto;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductoRepository extends JpaRepository<Producto, Long>, JpaSpecificationExecutor<Producto> {
    java.util.List<Producto> findByActivoTrueAndEliminadoFalseOrderByNombreAsc();
    Optional<Producto> findByIdAndEliminadoFalse(Long id);
    boolean existsByCodigoIgnoreCase(String codigo);
    Optional<Producto> findByCodigoIgnoreCase(String codigo);
    long countByCategoriaIdAndActivoTrueAndEliminadoFalse(Long categoriaId);
    long countByMarcaIdAndActivoTrueAndEliminadoFalse(Long marcaId);
    long countByActivoTrueAndEliminadoFalse();

    @Query("select count(p) from Producto p where p.activo = true and p.eliminado = false "
            + "and coalesce((select sum(s.cantidad) from Stock s where s.producto.id = p.id "
            + "and s.eliminado = false), 0) > 0")
    long contarDisponibles();

    @Query("select count(p) from Producto p where p.activo = true and p.eliminado = false "
            + "and coalesce((select sum(s.cantidad) from Stock s where s.producto.id = p.id "
            + "and s.eliminado = false), 0) = 0")
    long contarAgotados();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Producto p where p.id = :id and p.activo = true and p.eliminado = false")
    Optional<Producto> bloquearActivo(@Param("id") Long id);
}
