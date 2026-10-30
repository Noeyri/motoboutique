package com.motoboutique.catalogo.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.motoboutique.catalogo.service.CatalogoService;
import com.motoboutique.catalogo.service.ImportacionCatalogoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalogo-auth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.seed.enabled=false",
        "app.cors.allowed-origins=http://localhost:5173"
})
@AutoConfigureMockMvc
class CatalogoAutorizacionTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private CatalogoService servicio;
    @MockitoBean private ImportacionCatalogoService importacionServicio;

    @Test
    void consultaPuedeLeerCatalogo() throws Exception {
        mockMvc.perform(get("/api/catalogo/categorias")
                        .with(user("almacenero").roles("ALMACENERO")
                                .authorities(() -> "PRODUCTO_VER")))
                .andExpect(status().isOk());
    }

    @Test
    void otroRolSinPermisoRecibe403() throws Exception {
        mockMvc.perform(get("/api/catalogo/categorias")
                        .with(user("contador").roles("CONTADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void sesionAusenteRecibe401() throws Exception {
        mockMvc.perform(get("/api/catalogo/categorias")
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-CSRF-TOKEN"))
                .andExpect(header().string("Access-Control-Expose-Headers", "X-CSRF-TOKEN"));
    }

    @Test
    void permisoDeEdicionPermiteCrearCategoria() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/catalogo/categorias")
                        .with(user("admin").roles("ALMACENERO")
                                .authorities(() -> "PRODUCTO_EDITAR"))
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Accesorios","descripcion":"Catálogo","activo":true}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void contadorNoPuedeCrearCategorias() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/catalogo/categorias")
                        .with(user("contador").roles("CONTADOR")
                                .authorities(() -> "PRODUCTO_VER", () -> "REPORTE_VER"))
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Accesorios","descripcion":"Catálogo","activo":true}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void consultaNoPuedeCrearProductos() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/catalogo/productos")
                        .with(user("consulta").roles("CONSULTA")
                                .authorities(() -> "PRODUCTO_VER", () -> "INVENTARIO_VER"))
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"codigo":"P-999","nombre":"Producto consulta","categoriaId":1,
                                 "marcaId":1,"precioCompra":10.00,"precioVenta":20.00,
                                 "stockMinimo":1,"unidad":"UNIDAD","activo":true}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void permisoDeImportacionEsIndependienteDelPermisoDeLectura() throws Exception {
        mockMvc.perform(get("/api/catalogo/importaciones")
                        .with(user("almacenero").roles("ALMACENERO")
                                .authorities(() -> "IMPORTAR_CATALOGO")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/catalogo/importaciones")
                        .with(user("consulta").roles("CONSULTA")
                                .authorities(() -> "PRODUCTO_VER")))
                .andExpect(status().isForbidden());
    }
}
