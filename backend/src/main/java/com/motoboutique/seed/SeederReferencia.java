package com.motoboutique.seed;

import java.math.BigDecimal;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(30)
@ConditionalOnProperty(name = "app.seed.referencia", havingValue = "true")
class SeederReferencia implements SeederFase {
    private record ProductoRef(String codigo, String nombre, String categoria, String marca, BigDecimal compra, BigDecimal venta) {}

    private final JdbcTemplate jdbc;

    SeederReferencia(JdbcTemplate jdbc, @Value("${app.seed.referencia:false}") boolean habilitado) {
        this.jdbc = jdbc;
    }

    @Override
    public void ejecutar() {
        jdbc.update("INSERT INTO almacenes(codigo,nombre,direccion) VALUES ('ALM-PRINCIPAL','Principal','Almacén principal') ON CONFLICT(codigo) DO NOTHING");
        jdbc.update("INSERT INTO almacenes(codigo,nombre,direccion) VALUES ('ALM-NORTE','Norte','Almacén Norte') ON CONFLICT(codigo) DO NOTHING");
        for (String nombre : List.of("Protección","Lubricantes","Motor","Frenos","Transmisión","Eléctrico")) {
            asegurarCategoria(nombre);
        }
        for (String nombre : List.of("LS2","Motul","NGK","Brembo","DID","Yuasa","Yamaha","Honda","Suzuki")) {
            asegurarMarca(nombre);
        }
        crearModelo("Yamaha", "FZ-S", 149, "NAKED");
        crearModelo("Honda", "CB190R", 184, "NAKED");
        crearModelo("Suzuki", "GN 125", 125, "CALLE");
        crearModelo("Yamaha", "YBR 125", 125, "CALLE");

        List<ProductoRef> productos = List.of(
                producto("PRD-0001","Casco integral LS2","Protección","LS2","480.00","620.00"),
                producto("PRD-0002","Aceite Motul 10W40","Lubricantes","Motul","29.00","45.00"),
                producto("PRD-0003","Bujía NGK estándar","Motor","NGK","18.00","28.00"),
                producto("PRD-0004","Pastilla de freno Brembo","Frenos","Brembo","68.00","95.00"),
                producto("PRD-0005","Cadena DID 428","Transmisión","DID","85.00","129.00"),
                producto("PRD-0006","Batería Yuasa 12V","Eléctrico","Yuasa","165.00","245.00"),
                producto("PRD-0007","Aceite Motul transmisión","Lubricantes","Motul","25.00","39.00"),
                producto("PRD-0008","Filtro de aceite LS","Motor","LS2","12.00","22.00"),
                producto("PRD-0009","Filtro de aire deportivo","Motor","Motul","35.00","58.00"),
                producto("PRD-0010","Disco de freno Brembo","Frenos","Brembo","125.00","180.00"),
                producto("PRD-0011","Liquido de frenos DOT4","Frenos","Motul","18.00","32.00"),
                producto("PRD-0012","Guantes de protección","Protección","LS2","55.00","89.00"),
                producto("PRD-0013","Mica para casco LS2","Protección","LS2","38.00","65.00"),
                producto("PRD-0014","Sprocket DID delantero","Transmisión","DID","42.00","69.00"),
                producto("PRD-0015","Sprocket DID trasero","Transmisión","DID","48.00","79.00"),
                producto("PRD-0016","Bujía NGK iridium","Motor","NGK","34.00","52.00"),
                producto("PRD-0017","Batería Yuasa sellada","Eléctrico","Yuasa","180.00","265.00"),
                producto("PRD-0018","Manilla deportiva","Eléctrico","Yamaha","28.00","49.00"),
                producto("PRD-0019","Sensor de velocidad","Eléctrico","Honda","75.00","115.00"),
                producto("PRD-0020","Kit completo de transmisión","Transmisión","DID","210.00","310.00"));

        for (ProductoRef p : productos) crearProducto(p);
        jdbc.update("INSERT INTO proveedores(codigo,razon_social,ruc,telefono,activo) VALUES ('PROV-001','MotoImport S.A.C.','20104275184','999820114',true) ON CONFLICT(codigo) DO NOTHING");
        jdbc.update("INSERT INTO proveedores(codigo,razon_social,ruc,telefono,activo) VALUES ('PROV-002','Distribuidora Racing','20518490271','988102455',true) ON CONFLICT(codigo) DO NOTHING");
        jdbc.update("INSERT INTO proveedores(codigo,razon_social,ruc,telefono,activo) VALUES ('PROV-003','Lubri Perú','20658410225','977401210',true) ON CONFLICT(codigo) DO NOTHING");

        Long principal = jdbc.queryForObject("SELECT id FROM almacenes WHERE codigo='ALM-PRINCIPAL'", Long.class);
        for (int i = 0; i < productos.size(); i++) {
            ProductoRef p = productos.get(i);
            Long productoId = jdbc.queryForObject("SELECT id FROM productos WHERE codigo=?", Long.class, p.codigo());
            String referencia = "REF-ENT-SEED-" + p.codigo();
            Integer existe = jdbc.queryForObject("SELECT count(*) FROM movimientos_inventario WHERE referencia=?", Integer.class, referencia);
            if (Integer.valueOf(0).equals(existe)) {
                BigDecimal cantidad = BigDecimal.valueOf(8 + (i % 5));
                jdbc.update("INSERT INTO movimientos_inventario(producto_id,almacen_id,tipo,cantidad,costo_unitario,saldo_cantidad,costo_promedio,referencia,motivo) VALUES (?, ?, 'ENTRADA', ?, ?, 0, ?, ?, 'Stock de referencia idempotente')",
                        productoId, principal, cantidad, p.compra(), p.compra(), referencia);
            }
        }
        log.info("Datos de referencia de Moto Boutique asegurados");
    }

    private ProductoRef producto(String codigo, String nombre, String categoria, String marca, String compra, String venta) {
        return new ProductoRef(codigo,nombre,categoria,marca,new BigDecimal(compra),new BigDecimal(venta));
    }

    /**
     * Inserta la categoría solo si nunca existió una con ese nombre, ni siquiera eliminada:
     * si un administrador la borró, el seeder no la vuelve a crear en cada arranque.
     * No usa ON CONFLICT(nombre): desde V59 la unicidad es un índice parcial
     * sobre lower(trim(nombre)) WHERE eliminado = false, que ON CONFLICT(nombre) no puede inferir.
     */
    private void asegurarCategoria(String nombre) {
        jdbc.update("""
            INSERT INTO categorias(nombre)
            SELECT ? WHERE NOT EXISTS (
                SELECT 1 FROM categorias WHERE lower(trim(nombre)) = lower(trim(?)))
            """, nombre, nombre);
    }

    /** Igual que {@link #asegurarCategoria}; desde V60 marcas usa el mismo índice parcial. */
    private void asegurarMarca(String nombre) {
        jdbc.update("""
            INSERT INTO marcas(nombre)
            SELECT ? WHERE NOT EXISTS (
                SELECT 1 FROM marcas WHERE lower(trim(nombre)) = lower(trim(?)))
            """, nombre, nombre);
    }

    private void crearModelo(String marca, String nombre, int cc, String tipo) {
        jdbc.update("INSERT INTO modelos_moto(marca_id,nombre,cilindrada_cc,tipo) SELECT id,?,?,? FROM marcas WHERE nombre=? AND eliminado=false ON CONFLICT(marca_id,nombre) DO UPDATE SET cilindrada_cc=EXCLUDED.cilindrada_cc,tipo=EXCLUDED.tipo", nombre, cc, tipo, marca);
    }

    private void crearProducto(ProductoRef p) {
        jdbc.update("""
            INSERT INTO productos(codigo,nombre,categoria_id,marca_id,precio_compra,precio_venta,stock_minimo,unidad,activo,especificaciones)
            SELECT ?, ?, c.id, m.id, ?, ?, 5, 'UNIDAD', true, '{}'::jsonb
            FROM categorias c, marcas m
            WHERE c.nombre=? AND c.eliminado=false AND m.nombre=? AND m.eliminado=false
            ON CONFLICT(codigo) DO UPDATE SET nombre=EXCLUDED.nombre, precio_compra=EXCLUDED.precio_compra, precio_venta=EXCLUDED.precio_venta
            """, p.codigo(), p.nombre(), p.compra(), p.venta(), p.categoria(), p.marca());
        jdbc.update("INSERT INTO producto_compatibilidades(producto_id,modelo_moto_id) SELECT p.id, mm.id FROM productos p JOIN modelos_moto mm ON mm.nombre IN ('FZ-S','CB190R') WHERE p.codigo=? ON CONFLICT DO NOTHING", p.codigo());
    }
}