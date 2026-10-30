package com.motoboutique.catalogo.repository;

import com.motoboutique.catalogo.domain.Categoria;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CategoriaRepository extends JpaRepository<Categoria, Long>, JpaSpecificationExecutor<Categoria> {
    Optional<Categoria> findByIdAndEliminadoFalse(Long id);
    Optional<Categoria> findByNombreIgnoreCaseAndEliminadoFalse(String nombre);
    boolean existsByNombreIgnoreCaseAndEliminadoFalse(String nombre);
}
