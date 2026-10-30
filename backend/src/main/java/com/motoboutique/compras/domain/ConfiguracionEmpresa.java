package com.motoboutique.compras.domain;

import com.motoboutique.inventario.domain.Almacen;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "configuracion_empresa")
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracionEmpresa {
    @Id
    private Short id;

    @Column(name = "razon_social", length = 180)
    private String razonSocial;

    @Column(length = 11)
    private String ruc;

    @Column(nullable = false, length = 3)
    private String moneda = "PEN";

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal igv;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "almacen_defecto_id")
    private Almacen almacenDefecto;

    @Column(name = "stock_minimo_default", nullable = false, precision = 14, scale = 3)
    private BigDecimal stockMinimoDefault = BigDecimal.ZERO;

    @Column(name = "actualizado_en", nullable = false)
    private java.time.Instant actualizadoEn;
}
