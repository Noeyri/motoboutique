package com.motoboutique.seguridad.web;

import com.motoboutique.seguridad.dto.UsuarioAutenticado;
import com.motoboutique.seguridad.dto.UsuarioCrearDto;
import com.motoboutique.seguridad.dto.UsuarioDto;
import com.motoboutique.seguridad.service.UsuarioService;
import com.motoboutique.shared.web.PageResponse;
import com.motoboutique.shared.exception.NegocioException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USUARIO_VER')")
    public PageResponse<UsuarioListado> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(required = false) String rol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "nombre") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        PageResponse<UsuarioDto> resultado = usuarioService.listar(q, activo, normalizarRol(rol),
                page, size, sort, direction);
        List<UsuarioListado> contenido = resultado.getContenido().stream()
                .map(u -> new UsuarioListado(u.getId(), u.getNombre(), u.getEmail(), u.getRoles(),
                        u.isActivo())).toList();
        return new PageResponse<>(contenido, resultado.getNumero(), resultado.getTamanio(),
                resultado.getTotalElementos(), resultado.getTotalPaginas());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USUARIO_EDITAR')")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioDto crear(@Valid @RequestBody UsuarioCrearDto datos) {
        return usuarioService.crear(datos);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USUARIO_EDITAR')")
    public void cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoUsuario estado,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        usuarioService.cambiarEstado(id, estado.activo(), actor.getId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USUARIO_EDITAR')")
    public UsuarioDto editar(@PathVariable Long id, @Valid @RequestBody UsuarioEditar datos,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        List<String> roles = datos.roles() == null || datos.roles().isEmpty()
                ? (normalizarRol(datos.rol()) == null ? List.of()
                    : List.of(normalizarRol(datos.rol())))
                : datos.roles().stream().map(UsuarioController::normalizarRol).toList();
        if (roles.isEmpty()) {
            throw new NegocioException("El usuario debe tener al menos un rol.");
        }
        return usuarioService.editar(id, datos.nombre(), datos.email(), roles, datos.activo(),
                actor.getId());
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restablecerPassword(@PathVariable Long id,
            @Valid @RequestBody PasswordAdminSolicitud solicitud) {
        usuarioService.restablecerPasswordAdmin(id, solicitud.password());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('USUARIO_EDITAR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        usuarioService.eliminar(id, actor.getId());
    }

    private static String normalizarRol(String rol) {
        if (rol == null || rol.isBlank()) {
            return null;
        }
        return switch (rol.toUpperCase(java.util.Locale.ROOT).trim()) {
            case "ADMINISTRADOR" -> "ADMIN";
            default -> rol.toUpperCase(java.util.Locale.ROOT).trim();
        };
    }

    public record UsuarioListado(Long id, String nombre, String email, List<String> roles,
                                 boolean activo) {}
    public record EstadoUsuario(@NotNull Boolean activo) {}
    public record UsuarioEditar(@NotBlank @Size(max = 120) String nombre,
            @NotBlank @Email @Size(max = 150) String email,
            String rol, @Size(max = 20) List<@NotBlank String> roles, Boolean activo) {}
    public record PasswordAdminSolicitud(@NotBlank @Size(min = 8, max = 72) String password) {}
}
