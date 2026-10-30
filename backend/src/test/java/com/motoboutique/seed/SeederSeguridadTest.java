package com.motoboutique.seed;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.motoboutique.seguridad.dto.PermisoDto;
import com.motoboutique.seguridad.dto.UsuarioCrearDto;
import com.motoboutique.seguridad.service.PermisoService;
import com.motoboutique.seguridad.service.RolService;
import com.motoboutique.seguridad.service.UsuarioService;
import com.motoboutique.shared.exception.NegocioException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SeederSeguridadTest {

    private static final String CORREO_ADMIN = "admin@motoboutique.local";

    @Mock
    private PermisoService permisoService;

    @Mock
    private RolService rolService;

    @Mock
    private UsuarioService usuarioService;

    @Captor
    private ArgumentCaptor<Collection<String>> permisosCaptor;

    private SeederSeguridad seeder(String password) {
        return new SeederSeguridad(permisoService, rolService, usuarioService, CORREO_ADMIN, password);
    }

    @Test
    void cargaLosPermisosLosRolesYCreaAlAdministrador() {
        seeder("Clave-Admin-1").ejecutar();

        for (String codigo : List.of("USUARIO_VER", "USUARIO_CREAR", "USUARIO_EDITAR", "ROL_VER", "ROL_EDITAR")) {
            verify(permisoService).asegurar(eq(codigo), anyString(), eq("seguridad"));
        }
        ArgumentCaptor<String> codigosRol = ArgumentCaptor.forClass(String.class);
        verify(rolService, times(4)).asegurar(codigosRol.capture(), anyString(), anyString(),
                eq(true), permisosCaptor.capture());
        assertEquals(List.of("ADMIN", "ALMACENERO", "CONTADOR", "CONSULTA"), codigosRol.getAllValues());

        ArgumentCaptor<UsuarioCrearDto> usuario = ArgumentCaptor.forClass(UsuarioCrearDto.class);
        verify(usuarioService).crear(usuario.capture());
        assertEquals(CORREO_ADMIN, usuario.getValue().getEmail());
        assertEquals("Clave-Admin-1", usuario.getValue().getPassword());
        assertEquals(Set.of("ADMIN"), usuario.getValue().getRoles());
    }

    @Test
    void asignaPermisosSegunElRolBase() {
        seeder("Clave-Admin-1").ejecutar();

        ArgumentCaptor<String> codigosRol = ArgumentCaptor.forClass(String.class);
        verify(rolService, times(4)).asegurar(codigosRol.capture(), anyString(), anyString(),
                eq(true), permisosCaptor.capture());
        Map<String, Integer> permisosPorRol = new HashMap<>();
        for (int i = 0; i < codigosRol.getAllValues().size(); i++) {
            permisosPorRol.put(codigosRol.getAllValues().get(i), permisosCaptor.getAllValues().get(i).size());
        }
        assertEquals(21, permisosPorRol.get("ADMIN"));
        assertEquals(10, permisosPorRol.get("ALMACENERO"));
        assertEquals(6, permisosPorRol.get("CONTADOR"));
        assertEquals(2, permisosPorRol.get("CONSULTA"));
    }

    @Test
    void agregaLosPermisosNuevosALosRolesExistentes() {
        PermisoDto anterior = new PermisoDto();
        anterior.setCodigo("PRODUCTO_VER");
        when(permisoService.listar()).thenReturn(List.of(anterior));

        seeder("Clave-Admin-1").ejecutar();

        ArgumentCaptor<String> codigosRol = ArgumentCaptor.forClass(String.class);
        verify(rolService, times(4)).agregarPermisos(codigosRol.capture(), permisosCaptor.capture());
        Map<String, Collection<String>> agregadosPorRol = new HashMap<>();
        for (int i = 0; i < codigosRol.getAllValues().size(); i++) {
            agregadosPorRol.put(codigosRol.getAllValues().get(i), permisosCaptor.getAllValues().get(i));
        }
        assertEquals(21, agregadosPorRol.get("ADMIN").size());
        assertEquals(List.of("INVENTARIO_VER"), agregadosPorRol.get("CONSULTA"));
        assertFalse(agregadosPorRol.get("ALMACENERO").contains("PRODUCTO_VER"));
    }

    @Test
    void noTocaAlAdministradorSiYaExiste() {
        when(usuarioService.existeEmail(CORREO_ADMIN)).thenReturn(true);

        seeder("Clave-Admin-1").ejecutar();

        verify(usuarioService, never()).crear(any());
    }

    @Test
    void noCreaAlAdministradorSiFaltaLaContrasena() {
        seeder("").ejecutar();

        verify(usuarioService, never()).crear(any());
    }

    @Test
    void unaContrasenaInvalidaNoDetieneElArranque() {
        when(usuarioService.crear(any())).thenThrow(new NegocioException("La contraseña es demasiado corta."));

        assertDoesNotThrow(() -> seeder("corta").ejecutar());
    }
}
