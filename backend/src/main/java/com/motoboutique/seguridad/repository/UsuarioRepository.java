package com.motoboutique.seguridad.repository;

import com.motoboutique.seguridad.domain.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Solo lo usa el módulo seguridad. */
public interface UsuarioRepository extends JpaRepository<Usuario, Long>, JpaSpecificationExecutor<Usuario> {
    long countByActivoTrueAndEliminadoFalse();

    /** Carga roles y permisos en una sola consulta: es lo que necesita el inicio de sesión. */
    @EntityGraph(attributePaths = {"roles", "roles.permisos"})
    Optional<Usuario> findByEmailIgnoreCaseAndEliminadoFalse(String email);

    /** Permite iniciar sesión también con el nombre/alias mostrado en la tabla usuarios. */
    @EntityGraph(attributePaths = {"roles", "roles.permisos"})
    Optional<Usuario> findByNombreIgnoreCaseAndEliminadoFalse(String nombre);

    /** Incluye eliminados a propósito: el índice único de correo los cuenta también. */
    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    @Query("select count(distinct u.id) from Usuario u join u.roles r "
            + "where r.codigo = :codigo and u.activo = true and u.eliminado = false")
    long contarActivosConRol(@Param("codigo") String codigo);

    @EntityGraph(attributePaths = "roles")
    @Query("select distinct u from Usuario u where u.id = :id")
    Optional<Usuario> buscarConRoles(@Param("id") Long id);
}
