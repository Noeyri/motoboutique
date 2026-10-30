package com.motoboutique.seguridad.repository;

import com.motoboutique.seguridad.domain.TokenRecuperacion;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Solo lo usa el módulo seguridad. */
public interface TokenRecuperacionRepository extends JpaRepository<TokenRecuperacion, Long> {

    @EntityGraph(attributePaths = "usuario")
    Optional<TokenRecuperacion> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "usuario")
    @Query("select t from TokenRecuperacion t where t.tokenHash = :hash and t.eliminado = false")
    Optional<TokenRecuperacion> bloquearVigentePorHash(@Param("hash") String hash);

    @Modifying(flushAutomatically = true)
    @Query("update TokenRecuperacion t set t.usadoEn = :ahora "
            + "where t.usuario.id = :usuarioId and t.usadoEn is null and t.eliminado = false")
    int invalidarNoUsadosPorUsuario(@Param("usuarioId") Long usuarioId, @Param("ahora") java.time.Instant ahora);
}
