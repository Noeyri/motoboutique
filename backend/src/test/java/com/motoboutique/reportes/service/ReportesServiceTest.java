package com.motoboutique.reportes.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.motoboutique.compras.repository.ConfiguracionEmpresaRepository;
import com.motoboutique.compras.repository.OrdenCompraRepository;
import com.motoboutique.inventario.dto.MovimientoDto;
import com.motoboutique.inventario.service.InventarioConsultaService;
import com.motoboutique.reportes.dto.ReporteDto;
import com.motoboutique.shared.web.PageResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportesServiceTest {
    @Mock private InventarioConsultaService inventarioService;
    @Mock private ValorizacionService valorizacionService;
    @Mock private OrdenCompraRepository ordenRepository;
    @Mock private ConfiguracionEmpresaRepository configuracionRepository;
    @InjectMocks private ReportesService service;

    private static MovimientoDto movimiento(int numero, String tipo) {
        return new MovimientoDto((long) numero, "M-" + numero, Instant.EPOCH, tipo, 1L, "P-001",
                "Producto", 1L, "Principal", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.TEN, null, null, "admin");
    }

    private static PageResponse<MovimientoDto> pagina(int numero, int filas, String tipo) {
        return new PageResponse<>(IntStream.range(0, filas).mapToObj(i -> movimiento(i, tipo)).toList(),
                numero, 100, 250, 3);
    }

    @Test
    void generarCompletoJuntaTodasLasPaginasYSumaLosTotalesPorPagina() {
        when(configuracionRepository.findById((short) 1)).thenReturn(Optional.empty());
        when(inventarioService.listarMovimientos(isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), anyInt(), eq(100), eq("fecha"), eq("desc")))
                .thenReturn(pagina(0, 100, "ENTRADA"))
                .thenReturn(pagina(1, 100, "SALIDA"))
                .thenReturn(pagina(2, 50, "ENTRADA"));

        ReporteDto reporte = service.generarCompleto("movimientos-por-periodo", null, null, null,
                null, null, null, null);

        assertEquals(250, reporte.datos().getContenido().size());
        assertEquals(250, reporte.datos().getTotalElementos());
        assertEquals("150", reporte.totales().get("entradasPagina"));
        assertEquals("100", reporte.totales().get("salidasPagina"));
    }

    @Test
    void generarMantieneLaPaginaPedida() {
        when(configuracionRepository.findById((short) 1)).thenReturn(Optional.empty());
        when(inventarioService.listarMovimientos(isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), eq(1), eq(100), eq("fecha"), eq("desc")))
                .thenReturn(pagina(1, 100, "SALIDA"));

        ReporteDto reporte = service.generar("movimientos-por-periodo", null, null, null, null,
                null, null, null, 1, 500);

        assertEquals(100, reporte.datos().getContenido().size());
        assertEquals(List.of("Documento", "Fecha", "Tipo", "Código", "Producto", "Cantidad",
                "Costo unitario", "Saldo"), reporte.columnas());
    }
}
