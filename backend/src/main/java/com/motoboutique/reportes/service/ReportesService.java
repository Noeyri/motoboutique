package com.motoboutique.reportes.service;

import com.motoboutique.compras.repository.ConfiguracionEmpresaRepository;
import com.motoboutique.compras.repository.OrdenCompraRepository;
import com.motoboutique.inventario.dto.MovimientoDto;
import com.motoboutique.inventario.dto.StockInventarioDto;
import com.motoboutique.inventario.service.InventarioConsultaService;
import com.motoboutique.reportes.dto.ReporteDto;
import com.motoboutique.reportes.dto.ValorizacionDto;
import com.motoboutique.shared.exception.NegocioException;
import com.motoboutique.shared.web.PageResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportesService {
    private final InventarioConsultaService inventarioService;
    private final ValorizacionService valorizacionService;
    private final OrdenCompraRepository ordenRepository;
    private final ConfiguracionEmpresaRepository configuracionRepository;

    /** Tope de filas de una exportación, para no generar archivos sin límite. */
    public static final int MAX_FILAS_EXPORTACION = 10_000;
    private static final int TAMANIO_LOTE = 100;

    @Transactional(readOnly = true)
    public ReporteDto generar(String tipo, LocalDate fechaCorte, Long almacenId, Long categoriaId,
                              Long productoId, Instant desde, Instant hasta, String q,
                              int page, int size) {
        String normalizado = normalizarTipo(tipo, desde, hasta);
        Datos datos = cargar(normalizado, fechaCorte, almacenId, categoriaId, productoId, desde,
                hasta, q, Math.max(page, 0), Math.max(1, Math.min(size, TAMANIO_LOTE)));
        return reporte(normalizado, datos);
    }

    /**
     * Reporte con todas las filas que cumplen los filtros (hasta {@link #MAX_FILAS_EXPORTACION}),
     * para exportar. Recorre las páginas porque las consultas limitan cada página a 100 filas.
     */
    @Transactional(readOnly = true)
    public ReporteDto generarCompleto(String tipo, LocalDate fechaCorte, Long almacenId,
                                      Long categoriaId, Long productoId, Instant desde,
                                      Instant hasta, String q) {
        String normalizado = normalizarTipo(tipo, desde, hasta);
        List<List<String>> filas = new ArrayList<>();
        Map<String, String> totales = new LinkedHashMap<>();
        Datos datos;
        int pagina = 0;
        do {
            datos = cargar(normalizado, fechaCorte, almacenId, categoriaId, productoId, desde,
                    hasta, q, pagina, TAMANIO_LOTE);
            filas.addAll(datos.filas());
            acumularTotales(totales, datos.totales());
            pagina++;
        } while (pagina < datos.totalPaginas() && filas.size() < MAX_FILAS_EXPORTACION);
        List<List<String>> exportadas = filas.size() > MAX_FILAS_EXPORTACION
                ? filas.subList(0, MAX_FILAS_EXPORTACION) : filas;
        return reporte(normalizado, new Datos(datos.columnas(), datos.totalElementos(), 1, 0,
                Math.max(1, exportadas.size()), exportadas, totales));
    }

    private String normalizarTipo(String tipo, Instant desde, Instant hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new NegocioException("La fecha desde no puede ser posterior a la fecha hasta.");
        }
        return tipo.toLowerCase(java.util.Locale.ROOT);
    }

    // Los totales "...Pagina" solo cubren las filas de cada página: al juntar páginas se suman.
    // Los demás ya son del reporte completo y se repiten igual en cada página.
    private void acumularTotales(Map<String, String> acumulados, Map<String, String> pagina) {
        pagina.forEach((clave, valor) -> {
            if (clave.endsWith("Pagina") && acumulados.containsKey(clave)) {
                acumulados.put(clave, texto(new BigDecimal(acumulados.get(clave))
                        .add(new BigDecimal(valor))));
            } else {
                acumulados.putIfAbsent(clave, valor);
            }
        });
    }

    private ReporteDto reporte(String tipo, Datos datos) {
        var empresa = configuracionRepository.findById((short) 1).orElse(null);
        return new ReporteDto(tipo, empresa == null ? "" : empresa.getRazonSocial(),
                empresa == null ? "" : empresa.getRuc(), Instant.now(), datos.columnas(),
                datos.pagina(), datos.totales());
    }

    private Datos cargar(String normalizado, LocalDate fechaCorte, Long almacenId,
                         Long categoriaId, Long productoId, Instant desde, Instant hasta,
                         String q, int pagina, int tamanio) {
        String busqueda = q == null || q.isBlank() ? null : q.trim();
        return switch (normalizado) {
            case "valorizacion" -> valorizacion(fechaCorte, almacenId, categoriaId,
                    busqueda, pagina, tamanio);
            case "kardex" -> kardex(productoId, almacenId, desde, hasta, pagina, tamanio);
            case "stock-bajo" -> stockBajo(almacenId, categoriaId, busqueda, pagina, tamanio);
            case "movimientos-por-periodo" -> movimientos(almacenId, productoId, desde, hasta,
                    busqueda, pagina, tamanio);
            case "compras-por-proveedor" -> comprasProveedor(desde, hasta, busqueda, pagina, tamanio);
            case "por-categoria" -> categorias(fechaCorte, almacenId, categoriaId,
                    busqueda, pagina, tamanio);
            default -> throw new NegocioException("El tipo de reporte indicado no existe.");
        };
    }

    private Datos valorizacion(LocalDate fechaCorte, Long almacenId, Long categoriaId,
                               String q, int page, int size) {
        ValorizacionDto reporte = valorizacionService.valorizar(fechaCorte, almacenId,
                categoriaId, q, page, size);
        List<List<String>> filas = reporte.productos().getContenido().stream().map(p -> List.of(
                texto(p.codigo()), texto(p.producto()), texto(p.categoria()),
                texto(p.cantidad()), texto(p.costoPromedio()), texto(p.valor()))).toList();
        Map<String, String> totales = new LinkedHashMap<>();
        totales.put("cantidadTotal", texto(reporte.cantidadTotal()));
        totales.put("valorTotal", texto(reporte.valorTotal()));
        return new Datos(List.of("Código", "Producto", "Categoría", "Cantidad",
                "Costo promedio", "Valor"), reporte.productos().getTotalElementos(),
                reporte.productos().getTotalPaginas(), page, size, filas, totales);
    }

    private Datos kardex(Long productoId, Long almacenId, Instant desde, Instant hasta,
                        int page, int size) {
        if (productoId == null || almacenId == null) {
            throw new NegocioException("El reporte de kardex requiere productoId y almacenId.");
        }
        var kardex = inventarioService.kardex(productoId, almacenId, desde, hasta);
        var lineas = kardex.movimientos();
        List<List<String>> filas = lineas.stream().map(linea -> List.of(
                linea.fecha().toString(), linea.tipo(), texto(linea.entrada()),
                texto(linea.salida()), texto(linea.saldo()), texto(linea.costoPromedio()))).toList();
        int desdeFila = (int) Math.min((long) page * size, filas.size());
        int hastaFila = Math.min(desdeFila + size, filas.size());
        BigDecimal entradas = lineas.stream().map(linea -> linea.entrada() == null
                ? BigDecimal.ZERO : linea.entrada()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal salidas = lineas.stream().map(linea -> linea.salida() == null
                ? BigDecimal.ZERO : linea.salida()).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, String> totales = Map.of(
                "saldoInicial", texto(kardex.saldoInicial()),
                "saldoFinal", texto(lineas.isEmpty() ? kardex.saldoInicial()
                        : lineas.get(lineas.size() - 1).saldo()),
                "entradas", texto(entradas), "salidas", texto(salidas));
        return datos(List.of("Fecha", "Tipo", "Entrada", "Salida", "Saldo", "Costo promedio"),
                filas.subList(desdeFila, hastaFila), page, size, totales, filas.size());
    }

    private Datos stockBajo(Long almacenId, Long categoriaId, String q, int page, int size) {
        PageResponse<StockInventarioDto> stock = inventarioService.listarStock(q, almacenId,
                categoriaId, "BAJO", page, size, "cantidad", "asc");
        List<List<String>> filas = stock.getContenido().stream().map(item -> List.of(
                item.codigo(), item.nombre(), item.categoria(), texto(item.cantidadTotal()),
                texto(item.stockMinimo()), item.estado(), item.almacen())).toList();
        return new Datos(List.of("Código", "Producto", "Categoría", "Stock total",
                "Mínimo", "Estado", "Almacén"), stock.getTotalElementos(),
                stock.getTotalPaginas(), page, size, filas, Map.of("productos", 
                Long.toString(stock.getTotalElementos())));
    }

    private Datos movimientos(Long almacenId, Long productoId, Instant desde, Instant hasta,
                              String q, int page, int size) {
        PageResponse<MovimientoDto> movimientos = inventarioService.listarMovimientos(null,
                almacenId, productoId, desde, hasta, q, page, size, "fecha", "desc");
        List<List<String>> filas = movimientos.getContenido().stream().map(m -> List.of(
                texto(m.numero()), m.fecha().toString(), m.tipo(), m.productoCodigo(),
                m.productoNombre(), texto(m.cantidad()), texto(m.costoUnitario()),
                texto(m.saldoCantidad()))).toList();
        BigDecimal entradas = movimientos.getContenido().stream()
                .filter(m -> esEntrada(m.tipo())).map(MovimientoDto::cantidad)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal salidas = movimientos.getContenido().stream()
                .filter(m -> esSalida(m.tipo())).map(MovimientoDto::cantidad)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Datos(List.of("Documento", "Fecha", "Tipo", "Código", "Producto",
                "Cantidad", "Costo unitario", "Saldo"), movimientos.getTotalElementos(),
                movimientos.getTotalPaginas(), page, size,
                filas, Map.of("entradasPagina", texto(entradas), "salidasPagina", texto(salidas)));
    }

    private Datos comprasProveedor(Instant desde, Instant hasta, String q, int page, int size) {
        var filas = ordenRepository.totalesPorProveedor(desde, hasta, q, size, (long) page * size);
        long totalFilas = ordenRepository.contarProveedoresParaReporte(desde, hasta, q);
        List<List<String>> contenido = filas.stream().map(f -> List.of(f.getProveedor(),
                Long.toString(f.getOrdenes()), texto(f.getTotal()))).toList();
        BigDecimal total = ordenRepository.totalProveedoresParaReporte(desde, hasta, q);
        return new Datos(List.of("Proveedor", "Órdenes", "Total"), totalFilas,
                paginas(totalFilas, size), page, size, contenido,
                Map.of("totalCompras", texto(total)));
    }

    private Datos categorias(LocalDate fechaCorte, Long almacenId, Long categoriaId,
                             String q, int page, int size) {
        ValorizacionDto valores = valorizacionService.valorizar(fechaCorte, almacenId,
                categoriaId, q, 0, 100);
        List<List<String>> todas = valores.categorias().stream().map(c -> List.of(
                c.categoria(), texto(c.cantidad()), texto(c.valor()))).toList();
        int desdeFila = (int) Math.min((long) page * size, todas.size());
        int hastaFila = Math.min(desdeFila + size, todas.size());
        return datos(List.of("Categoría", "Cantidad", "Valor"),
                todas.subList(desdeFila, hastaFila), page, size,
                Map.of("cantidadTotal", texto(valores.cantidadTotal()),
                        "valorTotal", texto(valores.valorTotal())),
                todas.size());
    }

    private Datos datos(List<String> columnas, List<List<String>> filas, int page, int size,
                        Map<String, String> totales, long total) {
        return new Datos(columnas, total, paginas(total, size), page, size, filas, totales);
    }

    private int paginas(long total, int size) {
        return (int) Math.ceil((double) total / size);
    }

    private String texto(Object value) {
        return value == null ? "" : value.toString();
    }

    private boolean esEntrada(String tipo) {
        return List.of("ENTRADA", "TRANSFERENCIA_ENTRADA", "AJUSTE_POSITIVO", "RECEPCION")
                .contains(tipo);
    }

    private boolean esSalida(String tipo) {
        return List.of("SALIDA", "TRANSFERENCIA_SALIDA", "AJUSTE_NEGATIVO").contains(tipo);
    }

    private record Datos(List<String> columnas, long totalElementos, int totalPaginas,
                         int page, int size, List<List<String>> filas, Map<String, String> totales) {
        PageResponse<List<String>> pagina() {
            return new PageResponse<>(filas, page, size, totalElementos, totalPaginas);
        }
    }
}
