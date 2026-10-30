package com.motoboutique.reportes.web;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.motoboutique.reportes.dto.DashboardDto;
import com.motoboutique.reportes.dto.ReporteDto;
import com.motoboutique.reportes.service.DashboardService;
import com.motoboutique.reportes.service.ExportadorReportes;
import com.motoboutique.reportes.service.ReportesService;
import com.motoboutique.shared.web.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reportes-auth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.seed.enabled=false",
        "app.cors.allowed-origins=http://localhost:5173"
})
@AutoConfigureMockMvc
class ReportesAutorizacionTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private DashboardService dashboardService;
    @MockitoBean private ReportesService reportesService;
    @MockitoBean private ExportadorReportes exportador;

    @Test
    void dashboardAlmacenRequierePermisoDeInventario() throws Exception {
        when(dashboardService.obtener("almacen", 7))
                .thenReturn(new DashboardDto("almacen", Map.of(), List.of(), List.of(), List.of()));
        mockMvc.perform(get("/api/dashboard/almacen?dias=7")
                        .with(user("almacen").roles("ALMACENERO")
                                .authorities(() -> "INVENTARIO_VER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/dashboard/almacen?dias=7")
                        .with(user("consulta").roles("CONSULTA")
                                .authorities(() -> "PRODUCTO_VER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboardAdminRequiereRolAdmin() throws Exception {
        mockMvc.perform(get("/api/dashboard/admin")
                        .with(user("contador").roles("CONTADOR")
                                .authorities(() -> "REPORTE_VER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dashboard/admin")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void finanzasYConsultaUsanSusPermisosDeLectura() throws Exception {
        when(dashboardService.obtener("finanzas", null))
                .thenReturn(new DashboardDto("finanzas", Map.of(), List.of(), List.of(), List.of()));
        when(dashboardService.obtener("consulta", null))
                .thenReturn(new DashboardDto("consulta", Map.of(), List.of(), List.of(), List.of()));
        mockMvc.perform(get("/api/dashboard/finanzas")
                        .with(user("contador").roles("CONTADOR")
                                .authorities(() -> "REPORTE_VER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/dashboard/consulta")
                        .with(user("consulta").roles("CONSULTA")
                                .authorities(() -> "PRODUCTO_VER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/dashboard/consulta"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reportesRequierenPermisoDeConsultaYExportacion() throws Exception {
        when(reportesService.generar(org.mockito.ArgumentMatchers.eq("valorizacion"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(20)))
                .thenReturn(new ReporteDto("valorizacion", "Empresa", "20123456789",
                        Instant.now(), List.of("Producto"),
                        PageResponse.vacia(20), Map.of()));
        mockMvc.perform(get("/api/reportes/valorizacion")
                        .with(user("contador").roles("CONTADOR")
                                .authorities(() -> "REPORTE_VER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reportes/valorizacion?formato=csv")
                        .with(user("contador").roles("CONTADOR")
                                .authorities(() -> "REPORTE_VER")))
                .andExpect(status().isForbidden());
        when(reportesService.generarCompleto(org.mockito.ArgumentMatchers.eq("valorizacion"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(new ReporteDto("valorizacion", "Empresa", "20123456789",
                        Instant.now(), List.of("Producto"),
                        PageResponse.vacia(20), Map.of()));
        when(exportador.exportar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("csv")))
                .thenReturn("csv".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mockMvc.perform(get("/api/reportes/valorizacion?formato=csv")
                        .with(user("contador").roles("CONTADOR")
                                .authorities(() -> "REPORTE_VER", () -> "REPORTE_EXPORTAR")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reportes/valorizacion")
                        .with(user("consulta").roles("CONSULTA")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reportes/valorizacion"))
                .andExpect(status().isUnauthorized());
    }
}
