package com.motoboutique.inventario.web;

import com.motoboutique.inventario.service.AdministracionInventarioService;
import com.motoboutique.inventario.dto.AdministracionInventarioDto.AjusteDto;
import com.motoboutique.inventario.dto.AdministracionInventarioDto.ConteoDto;
import com.motoboutique.seguridad.dto.UsuarioAutenticado;
import com.motoboutique.shared.exception.NegocioException;
import com.motoboutique.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdministracionInventarioController {

    private final AdministracionInventarioService service;

    @PostMapping("/api/inventario/conteos")
    @PreAuthorize("hasAuthority('CONTEO_EDITAR') or hasRole('ADMIN')")
    public ConteoDto abrirConteo(@Valid @RequestBody AbrirConteoSolicitud solicitud,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return service.abrirConteo(solicitud.almacenId(), usuario.getId(), solicitud.observaciones());
    }

    @GetMapping("/api/inventario/conteos")
    @PreAuthorize("hasAuthority('CONTEO_EDITAR') or hasAuthority('INVENTARIO_VER') or hasRole('ADMIN')")
    public PageResponse<ConteoDto> listarConteos(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Long almacenId,
            @RequestParam(required = false) Instant desde,
            @RequestParam(required = false) Instant hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listarConteos(estado, almacenId, desde, hasta, page, size);
    }

    @GetMapping("/api/inventario/conteos/{id}")
    @PreAuthorize("hasAuthority('CONTEO_EDITAR') or hasAuthority('INVENTARIO_VER') or hasRole('ADMIN')")
    public ConteoDto obtenerConteo(@PathVariable Long id) {
        return service.obtenerConteo(id);
    }

    @PutMapping("/api/inventario/conteos/{id}/detalles/{productoId}")
    @PreAuthorize("hasAuthority('CONTEO_EDITAR') or hasRole('ADMIN')")
    public ConteoDto guardarConteo(@PathVariable Long id, @PathVariable Long productoId,
            @Valid @RequestBody ConteoCantidadSolicitud solicitud) {
        return service.guardarConteo(id, productoId, solicitud.cantidad());
    }

    @PostMapping("/api/inventario/conteos/{id}/cerrar")
    @PreAuthorize("hasAuthority('CONTEO_EDITAR') or hasRole('ADMIN')")
    public ConteoDto cerrarConteo(@PathVariable Long id,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return service.cerrarConteo(id, usuario.getId());
    }

    @PostMapping("/api/inventario/ajustes")
    @PreAuthorize("hasAuthority('AJUSTE_SOLICITAR') or hasRole('ADMIN')")
    public AjusteDto solicitarAjuste(@Valid @RequestBody AjusteSolicitud solicitud,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return service.solicitarAjuste(usuario.getId(), solicitud.aServicio());
    }

    @GetMapping("/api/inventario/ajustes")
    @PreAuthorize("hasAuthority('AJUSTE_APROBAR') or hasAuthority('AJUSTE_SOLICITAR') or hasRole('ADMIN')")
    public PageResponse<AjusteDto> listarAjustes(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) Long almacenId,
            @RequestParam(required = false) Long productoId,
            @RequestParam(required = false) Instant desde,
            @RequestParam(required = false) Instant hasta,
            @RequestParam(required = false) Long solicitanteId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        boolean puedeVerTodos = usuario.getAuthorities().stream()
                .anyMatch(a -> "AJUSTE_APROBAR".equals(a.getAuthority())
                        || "ROLE_ADMIN".equals(a.getAuthority()));
        Long solicitanteFiltrado = puedeVerTodos ? solicitanteId : usuario.getId();
        return service.listarAjustes(estado, almacenId, productoId, desde, hasta,
                solicitanteFiltrado, page, size);
    }

    @GetMapping("/api/aprobaciones")
    @PreAuthorize("hasAuthority('AJUSTE_APROBAR') or hasRole('ADMIN')")
    public PageResponse<AjusteDto> pendientes(
            @RequestParam(defaultValue = "PENDIENTE") String estado,
            @RequestParam(required = false) Long almacenId,
            @RequestParam(required = false) Long productoId,
            @RequestParam(required = false) Instant desde,
            @RequestParam(required = false) Instant hasta,
            @RequestParam(required = false) Long solicitanteId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listarAjustes(estado, almacenId, productoId, desde, hasta,
                solicitanteId, page, size);
    }

    @PostMapping("/api/aprobaciones/{id}/aprobar")
    @PreAuthorize("hasAuthority('AJUSTE_APROBAR') or hasRole('ADMIN')")
    public AjusteDto aprobar(@PathVariable Long id,
            @RequestBody(required = false) ComentarioSolicitud solicitud,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return service.aprobar(id, usuario.getId(), esAdmin(usuario),
                solicitud == null ? null : solicitud.comentario());
    }

    @PostMapping("/api/aprobaciones/{id}/rechazar")
    @PreAuthorize("hasAuthority('AJUSTE_APROBAR') or hasRole('ADMIN')")
    public AjusteDto rechazar(@PathVariable Long id, @Valid @RequestBody ComentarioSolicitud solicitud,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return service.rechazar(id, usuario.getId(), solicitud.comentario());
    }

    @PostMapping("/api/aprobaciones/lote")
    @PreAuthorize("hasAuthority('AJUSTE_APROBAR') or hasRole('ADMIN')")
    public List<AjusteDto> resolverLote(@Valid @RequestBody LoteAprobacionSolicitud solicitud,
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        if (!"APROBAR".equals(solicitud.accion()) && !"RECHAZAR".equals(solicitud.accion())) {
            throw new NegocioException("La acción debe ser APROBAR o RECHAZAR.");
        }
        return service.resolverLote(solicitud.ids(), usuario.getId(), esAdmin(usuario),
                solicitud.accion(), solicitud.comentario());
    }

    private boolean esAdmin(UsuarioAutenticado usuario) {
        return usuario.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    public record AbrirConteoSolicitud(@NotNull Long almacenId, @Size(max = 2000) String observaciones) {}
    public record ConteoCantidadSolicitud(@NotNull @DecimalMin("0.000")
            @Digits(integer = 11, fraction = 3) BigDecimal cantidad) {}
    public record AjusteSolicitud(@NotNull Long productoId, @NotNull Long almacenId,
            @NotNull @Digits(integer = 11, fraction = 3) BigDecimal cantidad,
            @NotBlank @Size(max = 255) String motivo) {
        com.motoboutique.inventario.dto.AdministracionInventarioDto.AjusteSolicitud aServicio() {
            return new com.motoboutique.inventario.dto.AdministracionInventarioDto.AjusteSolicitud(productoId, almacenId,
                    cantidad, motivo);
        }
    }
    public record ComentarioSolicitud(@Size(max = 2000) String comentario) {}
    public record LoteAprobacionSolicitud(@NotNull @Size(min = 1, max = 100) List<Long> ids,
            @NotBlank String accion, @Size(max = 2000) String comentario) {}

}
