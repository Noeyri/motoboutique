package com.motoboutique.reportes.web;

import com.motoboutique.reportes.dto.ReporteDto;
import com.motoboutique.reportes.service.ExportadorReportes;
import com.motoboutique.reportes.service.ReportesService;
import com.motoboutique.shared.exception.NegocioException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReportesController {
    private final ReportesService service;
    private final ExportadorReportes exportador;

    @GetMapping("/{tipo}")
    @PreAuthorize("hasAuthority('REPORTE_VER') or hasRole('ADMIN')")
    public ResponseEntity<?> reporte(
            @PathVariable String tipo,
            @RequestParam(required = false) LocalDate fechaCorte,
            @RequestParam(required = false) Long almacenId,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(required = false) Long productoId,
            @RequestParam(required = false) Instant desde,
            @RequestParam(required = false) Instant hasta,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String formato) {
        if (formato == null || formato.isBlank()) {
            return ResponseEntity.ok(service.generar(tipo, fechaCorte, almacenId, categoriaId,
                    productoId, desde, hasta, q, page, size));
        }
        String extension = formato.trim().toLowerCase(Locale.ROOT);
        if (!extension.equals("csv") && !extension.equals("xlsx") && !extension.equals("pdf")) {
            throw new NegocioException("El formato debe ser csv, xlsx o pdf.");
        }
        if (!tienePermisoExportacion()) {
            throw new AccessDeniedException("Se requiere el permiso REPORTE_EXPORTAR.");
        }
        // Se exportan todas las filas de los filtros, no solo la página que se ve en pantalla.
        ReporteDto reporte = service.generarCompleto(tipo, fechaCorte, almacenId, categoriaId,
                productoId, desde, hasta, q);
        MediaType mediaType = switch (extension) {
            case "csv" -> new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8);
            case "xlsx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            default -> MediaType.APPLICATION_PDF;
        };
        byte[] archivo = exportador.exportar(reporte, extension);
        return ResponseEntity.ok().contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("reporte-" + reporte.tipo() + "." + extension).build().toString())
                .body(archivo);
    }

    private boolean tienePermisoExportacion() {
        var autenticacion = SecurityContextHolder.getContext().getAuthentication();
        return autenticacion != null && autenticacion.getAuthorities().stream()
                .anyMatch(autoridad -> autoridad.getAuthority().equals("ROLE_ADMIN")
                        || autoridad.getAuthority().equals("REPORTE_EXPORTAR"));
    }
}
