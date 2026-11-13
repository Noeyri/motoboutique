package com.motoboutique.compras.repository;

import com.motoboutique.compras.domain.ConfiguracionEmpresa;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracionEmpresaRepository extends JpaRepository<ConfiguracionEmpresa, Short> {
    @Override
    @EntityGraph(attributePaths = "almacenDefecto")
    Optional<ConfiguracionEmpresa> findById(Short id);
}
