package com.motoboutique.seed;

import com.motoboutique.seguridad.dto.PermisoDto;
import com.motoboutique.seguridad.dto.UsuarioCrearDto;
import com.motoboutique.seguridad.service.PermisoService;
import com.motoboutique.seguridad.service.RolService;
import com.motoboutique.seguridad.service.UsuarioService;
import com.motoboutique.shared.exception.NegocioException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Datos iniciales de seguridad: permisos, roles base y el administrador.
 *
 * <p>La contraseña del administrador NUNCA está en el código: llega por la variable de entorno
 * ADMIN_PASSWORD (en desarrollo, desde el archivo .env). Si el administrador ya existe no se
 * modifica, y si falta la contraseña el sistema arranca igual y lo avisa en el log.</p>
 *
 * <p>Los permisos de catálogo, inventario, compras, etc. los agrega el seeder de cada fase
 * (el rol ADMIN los recibe todos).</p>
 */
@Slf4j
@Component
@Order(10)
class SeederSeguridad implements SeederFase {

    private static final String MODULO = "seguridad";

    private record PermisoBase(String codigo, String descripcion) {
    }

    private record RolBase(String codigo, String nombre, String descripcion, List<String> permisos) {
    }

    private static final List<PermisoBase> PERMISOS = List.of(
            new PermisoBase("USUARIO_VER", "Ver la lista y el detalle de los usuarios"),
            new PermisoBase("USUARIO_CREAR", "Crear usuarios"),
            new PermisoBase("USUARIO_EDITAR",
                    "Editar usuarios, activarlos o desactivarlos y restablecer su contraseña"),
            new PermisoBase("ROL_VER", "Ver los roles y sus permisos"),
            new PermisoBase("ROL_EDITAR", "Crear roles y modificar los permisos de un rol"),
            new PermisoBase("PRODUCTO_VER", "Ver el catálogo de productos"),
            new PermisoBase("PRODUCTO_EDITAR", "Crear y editar productos, marcas y categorías"),
            new PermisoBase("INVENTARIO_VER", "Consultar stock y movimientos"),
            new PermisoBase("INVENTARIO_EDITAR", "Registrar movimientos y ajustes de inventario"),
            new PermisoBase("AJUSTE_APROBAR", "Aprobar ajustes de inventario"),
            new PermisoBase("COMPRA_VER", "Consultar proveedores y órdenes de compra"),
            new PermisoBase("COMPRA_EDITAR", "Gestionar proveedores, órdenes y recepciones"),
            new PermisoBase("KARDEX_VER", "Consultar el kardex de inventario"),
            new PermisoBase("REPORTE_VER", "Consultar reportes y valorización"),
            new PermisoBase("REPORTE_EXPORTAR", "Exportar reportes"),
            new PermisoBase("CONFIG_VER", "Consultar la configuración de la empresa"),
            new PermisoBase("CONFIG_EDITAR", "Modificar la configuración de la empresa"),
            new PermisoBase("AJUSTE_SOLICITAR", "Solicitar ajustes de inventario"),
            new PermisoBase("CONTEO_EDITAR", "Crear y cerrar conteos físicos"),
            new PermisoBase("TRANSFERENCIA_EDITAR", "Crear y gestionar transferencias"),
            new PermisoBase("IMPORTAR_CATALOGO", "Importar productos y datos de catálogo"));

    private static final List<String> TODOS_LOS_PERMISOS = PERMISOS.stream()
            .map(PermisoBase::codigo)
            .toList();

    private static final List<RolBase> ROLES = List.of(
            new RolBase("ADMIN", "Administrador", "Acceso total al sistema", TODOS_LOS_PERMISOS),
            new RolBase("ALMACENERO", "Almacenero", "Catálogo, stock, compras y conteo físico", List.of("PRODUCTO_VER", "PRODUCTO_EDITAR", "INVENTARIO_VER", "INVENTARIO_EDITAR", "AJUSTE_SOLICITAR", "COMPRA_VER", "COMPRA_EDITAR", "CONTEO_EDITAR", "TRANSFERENCIA_EDITAR", "IMPORTAR_CATALOGO")),
            new RolBase("CONTADOR", "Contador", "Kardex, compras y reportes de consulta", List.of("PRODUCTO_VER", "INVENTARIO_VER", "COMPRA_VER", "KARDEX_VER", "REPORTE_VER", "REPORTE_EXPORTAR")),
            new RolBase("CONSULTA", "Consulta", "Consulta de catálogo y stock, sin modificar", List.of("PRODUCTO_VER", "INVENTARIO_VER")));

    private final PermisoService permisoService;
    private final RolService rolService;
    private final UsuarioService usuarioService;
    private final String adminEmail;
    private final String adminPassword;
    private final boolean resetPasswords;

    @Autowired
    SeederSeguridad(PermisoService permisoService,
                    RolService rolService,
                    UsuarioService usuarioService,
                    @Value("${ADMIN_EMAIL:admin@motoboutique.local}") String adminEmail,
                    @Value("${ADMIN_PASSWORD:}") String adminPassword,
                    @Value("${app.seed.reset-passwords:false}") boolean resetPasswords) {
        this.permisoService = permisoService;
        this.rolService = rolService;
        this.usuarioService = usuarioService;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.resetPasswords = resetPasswords;
    }

    SeederSeguridad(PermisoService permisoService, RolService rolService,
                    UsuarioService usuarioService, String adminEmail, String adminPassword) {
        this(permisoService, rolService, usuarioService, adminEmail, adminPassword, false);
    }

    @Override
    public void ejecutar() {
        Set<String> existentes = permisoService.listar().stream()
                .map(PermisoDto::getCodigo)
                .collect(Collectors.toSet());
        for (PermisoBase permiso : PERMISOS) {
            permisoService.asegurar(permiso.codigo(), permiso.descripcion(), MODULO);
        }
        for (RolBase rol : ROLES) {
            rolService.asegurar(rol.codigo(), rol.nombre(), rol.descripcion(), true, rol.permisos());
            // ADMIN siempre tiene todos los permisos. Los demás roles solo reciben los permisos nuevos
            // de esta versión, para no devolver los que un administrador quitó a propósito.
            List<String> agregar = "ADMIN".equals(rol.codigo())
                    ? rol.permisos()
                    : rol.permisos().stream().filter(codigo -> !existentes.contains(codigo)).toList();
            rolService.agregarPermisos(rol.codigo(), agregar);
        }
        crearAdministrador();
    }

    private void crearAdministrador() {
        if (adminEmail == null || adminEmail.isBlank()) {
            log.warn("No se creó el administrador: la variable ADMIN_EMAIL está vacía.");
            return;
        }
        if (usuarioService.existeEmail(adminEmail)) {
            if (resetPasswords && adminPassword != null && !adminPassword.isBlank()) {
                usuarioService.buscarPorEmail(adminEmail).ifPresent(usuario ->
                        usuarioService.cambiarPassword(usuario.getId(), adminPassword));
            }
            log.info("El administrador {} ya existe", adminEmail);
            return;
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("No se creó el administrador: falta la variable ADMIN_PASSWORD. "
                    + "Defínela en el archivo .env y vuelve a arrancar.");
            return;
        }
        try {
            usuarioService.crear(UsuarioCrearDto.de("admin", adminEmail, adminPassword,
                    null, Set.of("ADMIN")));
            log.info("Administrador creado: {}", adminEmail);
        } catch (NegocioException e) {
            log.error("No se pudo crear el administrador: {}", e.getMessage());
        }
    }
}
