package com.motoboutique.compras.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.motoboutique.compras.domain.ConfiguracionEmpresa;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ConfiguracionEmpresaRepositoryTest {

    @Autowired private ConfiguracionEmpresaRepository configuracionRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void cargaElAlmacenDefectoParaConvertirLaConfiguracionSinOpenInView() {
        entityManager.createNativeQuery("""
                INSERT INTO almacenes (
                    id, codigo, nombre, activo, creado_por, creado_en, version, eliminado
                ) VALUES (1, 'ALM-PRINCIPAL', 'Principal', TRUE, 'test', CURRENT_TIMESTAMP, 0, FALSE)
                """).executeUpdate();
        entityManager.createNativeQuery("""
                INSERT INTO configuracion_empresa (
                    id, moneda, igv, almacen_defecto_id, stock_minimo_default, actualizado_en
                ) VALUES (1, 'PEN', 18.00, 1, 0, CURRENT_TIMESTAMP)
                """).executeUpdate();
        entityManager.clear();

        ConfiguracionEmpresa configuracion = configuracionRepository.findById((short) 1).orElseThrow();

        assertTrue(Hibernate.isInitialized(configuracion.getAlmacenDefecto()));
        assertEquals("Principal", configuracion.getAlmacenDefecto().getNombre());
    }
}
