package com.motoboutique.administracion;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

import com.motoboutique.inventario.service.AdministracionInventarioService;
import com.motoboutique.inventario.domain.Almacen;
import com.motoboutique.compras.domain.ConfiguracionEmpresa;
import com.motoboutique.compras.repository.ConfiguracionEmpresaRepository;
import com.motoboutique.seguridad.service.RecuperacionPasswordService;
import com.motoboutique.seguridad.service.UsuarioService;
import com.motoboutique.seguridad.service.PermisoService;
import com.motoboutique.seguridad.service.RolService;
import com.motoboutique.shared.audit.AuditoriaService;
import com.motoboutique.shared.web.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.math.BigDecimal;
import java.util.Optional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin-auth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.seed.enabled=false",
        "app.cors.allowed-origins=http://localhost:5173"
})
@AutoConfigureMockMvc
class AdministracionAutorizacionTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private RolService rolService;
    @MockitoBean private PermisoService permisoService;
    @MockitoBean private AuditoriaService auditoriaService;
    @MockitoBean private AdministracionInventarioService inventarioService;
    @MockitoBean private UsuarioService usuarioService;
    @MockitoBean private RecuperacionPasswordService recuperacionService;
    @MockitoBean private ConfiguracionEmpresaRepository configuracionRepository;

    @Test
    void rolConPermisoPuedeConsultarRoles() throws Exception {
        mockMvc.perform(get("/api/roles")
                        .with(user("admin-roles").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void rolSinPermisoRecibe403AlConsultarRoles() throws Exception {
        mockMvc.perform(get("/api/roles")
                        .with(user("almacenero").roles("ALMACENERO")))
                .andExpect(status().isForbidden());
    }

    @Test
    void consultaDeRolesSinSesionRecibe401() throws Exception {
        mockMvc.perform(get("/api/roles"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void autoridadDeAprobacionPuedeListarAjustesPendientes() throws Exception {
        org.mockito.Mockito.when(inventarioService.listarAjustes(
                        org.mockito.ArgumentMatchers.eq("PENDIENTE"),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.isNull(),
                        org.mockito.ArgumentMatchers.eq(0),
                        org.mockito.ArgumentMatchers.eq(20)))
                .thenReturn(PageResponse.vacia(20));
        mockMvc.perform(get("/api/aprobaciones")
                        .with(user("aprobador").roles("CONTADOR")
                                .authorities(() -> "AJUSTE_APROBAR")))
                .andExpect(status().isOk());
    }

    @Test
    void rolSinPermisoNoPuedeListarAprobaciones() throws Exception {
        mockMvc.perform(get("/api/aprobaciones")
                        .with(user("consulta").roles("CONSULTA")))
                .andExpect(status().isForbidden());
    }

    @Test
    void usuarioSinSesionRecibe401AlConsultarAprobaciones() throws Exception {
        mockMvc.perform(get("/api/aprobaciones"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administradorPuedeFiltrarAuditoriaPorUsuarioAccionYFechas() throws Exception {
        when(auditoriaService.listar(isNull(), eq("admin"), eq("CREAR_USUARIO"),
                any(), any(), eq(0), eq(20))).thenReturn(PageResponse.vacia(20));
        mockMvc.perform(get("/api/auditoria")
                        .param("usuario", "admin")
                        .param("accion", "CREAR_USUARIO")
                        .param("desde", "2026-01-01T00:00:00Z")
                        .param("hasta", "2026-01-31T23:59:59Z")
                        .with(user("admin-audit").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void usuarioSinPermisoNoPuedeConsultarAuditoria() throws Exception {
        mockMvc.perform(get("/api/auditoria")
                        .with(user("almacenero").roles("ALMACENERO")))
                .andExpect(status().isForbidden());
    }

    @Test
    void consultaDeAuditoriaSinSesionRecibe401() throws Exception {
        mockMvc.perform(get("/api/auditoria"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioConPermisoPuedeConsultarUsuariosPaginados() throws Exception {
        org.mockito.Mockito.when(usuarioService.listar(null, null, null, 0, 20, "nombre", "asc"))
                .thenReturn(PageResponse.vacia(20));
        mockMvc.perform(get("/api/usuarios")
                        .with(user("admin-users").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void otroRolNoPuedeConsultarUsuarios() throws Exception {
        mockMvc.perform(get("/api/usuarios")
                        .with(user("consulta").roles("CONSULTA")))
                .andExpect(status().isForbidden());
    }

    @Test
    void solicitudDeRecuperacionEsPublicaYGenerica() throws Exception {
        mockMvc.perform(post("/api/auth/recuperar").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"persona@motoboutique.local\"}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void configuracionRequierePermisoDeLectura() throws Exception {
        ConfiguracionEmpresa config = new ConfiguracionEmpresa();
        Almacen almacen = new Almacen();
        almacen.setNombre("Principal");
        config.setId((short) 1);
        config.setMoneda("PEN");
        config.setIgv(new BigDecimal("18.00"));
        config.setStockMinimoDefault(BigDecimal.ZERO);
        config.setAlmacenDefecto(almacen);
        when(configuracionRepository.findById((short) 1)).thenReturn(Optional.of(config));
        mockMvc.perform(get("/api/configuracion")
                        .with(user("admin-config").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.almacenDefecto").value("Principal"));
        mockMvc.perform(get("/api/configuracion"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/configuracion")
                        .with(user("consulta").roles("CONSULTA")))
                .andExpect(status().isForbidden());
    }
}
