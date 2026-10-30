package com.motoboutique.catalogo.repository;

import com.motoboutique.catalogo.domain.Marca;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MarcaRepository extends JpaRepository<Marca, Long>, JpaSpecificationExecutor<Marca> {
    Optional<Marca> findByIdAndEliminadoFalse(Long id);
    Optional<Marca> findByNombreIgnoreCaseAndEliminadoFalse(String nombre);
    boolean existsByNombreIgnoreCaseAndEliminadoFalse(String nombre);
    List<Marca> findByEliminadoFalseAndActivoTrueOrderByNombre();
}
