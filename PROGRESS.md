# PROGRESS — Separación en backend y frontend

## Estado actual (30-10-2026)

> Las secciones siguientes son el historial de cada fase. Varios de sus "pendientes"
> (roles y permisos, movimientos, kardex, compras, reportes, recuperación de contraseña)
> ya están implementados. Esta sección es la referencia vigente.

**Funciona (probado contra PostgreSQL local):** login por correo o nombre de usuario,
usuarios, roles y permisos, configuración, aprobaciones, auditoría, catálogo (productos,
categorías, marcas, modelos, compatibilidades, importación), motos, inventario (stock,
entradas, salidas, transferencias, conteo físico, ajustes, kardex), compras (proveedores,
órdenes, recepciones), dashboards de los cuatro roles, valorización y reportes con
exportación CSV, XLSX y PDF. Las 40 rutas de lectura que usa el frontend responden 200.
También se probó en vivo el ciclo de escritura con saldos de kardex correctos en cada paso:
entrada, salida (y rechazo sin stock), orden de compra con recepción parcial y total,
transferencia entre almacenes, conteo físico, ajuste manual, aprobación y rechazo, y los
permisos de cada rol (consulta, contador, almacenero y admin).

**Pruebas:** backend con 119 pruebas unitarias y de MVC en verde
(`./mvnw test "-Dtest=*Test,!*PostgresTest" -Dsurefire.failIfNoSpecifiedTests=false`).
Las `*PostgresTest` usan Testcontainers y necesitan Docker.

**Pendientes reales:**
1. **Ventas:** el paquete `ventas` está vacío. Hoy una venta se registra como salida de
   inventario, sin cliente, comprobante ni precio de venta. Falta decidir el alcance.
2. **Correo:** recuperar contraseña necesita `SMTP_HOST`, `SMTP_USER` y `SMTP_PASSWORD` en el `.env`.
3. **Pruebas del frontend:** no hay; `npm test` solo revisa tipos.
4. **Pruebas con PostgreSQL real** (`*PostgresTest`): ejecutarlas con Docker antes de cada
   entrega. Los errores de tipado de parámetros nulos solo aparecen con PostgreSQL.
5. **Contraseñas expuestas:** un `.env` con contraseñas se subió a `main` (commit `48a781b`) y
   ya se quitó, pero sigue en el historial. Quien las use debe cambiarlas.

**Reglas a recordar:**
- `frontend/src/app/routes.ts` es a la vez el menú y la lista de rutas: quitar una entrada deja
  la pantalla en 404. Para ocultarla solo del menú usa `enMenu: false`.
- En consultas JPQL o nativas con parámetros opcionales, tipa el nulo (`cast(:x as String)`,
  `cast(:x as timestamptz)`); PostgreSQL no puede deducir el tipo de un parámetro nulo.
- El seeder da a ADMIN todos los permisos en cada arranque; a los demás roles solo les agrega
  permisos nuevos, para respetar los que un administrador quitó.

Fase: **separar el proyecto en dos aplicaciones independientes** sin perder lo existente.
Origen: https://github.com/mateo-pc/motoboutique (rama `main`, último commit `d31b7ec`). No se crearon ramas ni commits.

## 1. Punto de partida

El repositorio era un monolito Spring Boot con Thymeleaf. Estado real del código:

- **Implementado:** módulo `seguridad` (usuarios, roles, permisos, token de recuperación como entidad, `UsuarioDetailsService`, servicios, DTOs y mappers), auditoría JPA, `DataSeeder`, manejo de errores, `PageResponse`, migraciones `V1` (extensión `pg_trgm`) y `V2` (seguridad).
- **Solo esqueleto (paquetes vacíos con `package-info`):** catalogo, inventario, compras, motos, reportes, configuracion, ventas.
- **Interfaces:** solo existía una plantilla Thymeleaf, `error/general.html`. No había pantalla de login propia (se usaba la generada por Spring Security), ni controllers MVC, ni imágenes, ni CSS propio (Tailwind por CDN).

El diagrama de casos de uso (Sistema de Control de Inventario – Tienda de Motos y Accesorios) define cuatro actores —Administrador, Almacenero, Contador y Consulta— y los paquetes Reportes, Consulta, Administración, Movimientos de inventario, Catálogo y Compras. **Casi todo eso corresponde a fases posteriores**; en esta fase solo se conservaron los cuatro roles que el `SeederSeguridad` ya crea (ADMIN, ALMACENERO, CONTADOR, CONSULTA) y no se desarrolló ningún caso de uso nuevo.

## 2. Arquitectura resultante

```
motoboutique/
├─ backend/    API REST (Spring Boot, puerto 8081)
├─ frontend/   SPA (React + TypeScript + Vite, puerto 5173 en desarrollo)
├─ README.md   cómo ejecutar
└─ PROGRESS.md este documento
```

- **Comunicación:** HTTP + JSON. Autenticación por **sesión** (cookie `JSESSIONID`), la misma que usaba el sistema; no se cambió a JWT para no alterar el modelo de autenticación existente.
- **CSRF:** sigue activo. Cookie `XSRF-TOKEN` (legible por el frontend) + cabecera `X-XSRF-TOKEN` en los métodos que modifican datos.
- **Desarrollo:** el proxy de Vite reenvía `/api` al backend → mismo origen para el navegador. CORS queda configurado (`CORS_ALLOWED_ORIGINS`) para despliegues con orígenes distintos.
- **Errores:** `ProblemDetail` (RFC 9457) para todo, incluidos 401 y 403 generados dentro de la cadena de filtros de seguridad.
- **Capas del backend:** sin cambios (`web → service → repository → domain`, DTOs con MapStruct, paquete base `com.motoboutique`).

### API disponible

| Ruta | Función |
|---|---|
| `POST /api/auth/login` | Inicio de sesión (formulario con `email` y `password`). 204 / 401 |
| `GET /api/auth/me` | Usuario actual con roles y permisos. 200 / 401 |
| `POST /api/auth/logout` | Cierre de sesión. 204 |

### Frontend

- `src/api/`: cliente `fetch` (cookies, CSRF, normalización de `ProblemDetail`), tipos TypeScript de los DTO (`UsuarioDto`, `RolDto`, `PermisoDto`, `PageResponse`) y textos de error espejo de `MensajesHttp`.
- `src/auth/`: `AuthContext` (sesión, `tieneRol`, `tienePermiso`) y `RequireAuth` (ruta protegida).
- `src/pages/`: `ErrorPage` (migración fiel de `error/general.html`), `LoginPage`, `HomePage`.
- Rutas: `/login`, `/` (protegida), `*` → 404.
- Estilos: Tailwind CSS v4 instalado como dependencia (antes por CDN) con las mismas clases, colores (`#f6f5f3`, `#1c1917`, `#c2410c`, `#ea580c`, `#e7e5e4`, `#57534e`) y tipografía del sistema.

## 3. Cambios realizados

**Backend**
1. `pom.xml`: se quitaron `spring-boot-starter-thymeleaf`, `thymeleaf-extras-springsecurity6` y `spring-boot-starter-thymeleaf-test`. Resto de dependencias, Java 21 y procesadores (Lombok, MapStruct) intactos.
2. `SecurityConfig` reescrita para API: login/logout por JSON-friendly handlers (204/401), 401 y 403 en JSON, CSRF por cookie, CORS configurable. Se mantienen `@EnableMethodSecurity`, BCrypt, `UsuarioDetailsService` y el parámetro `email`.
3. Nuevas clases mínimas necesarias para el modelo SPA: `CsrfCookieFilter`, `RespuestasSeguridad`, `AuthController` (`GET /api/auth/me`) y `UsuarioSesionDto`.
4. `ApiExceptionHandler` ahora aplica a toda la aplicación (antes solo a `@RestController`).
5. Eliminados por ser exclusivos de vistas server-side: `GlobalExceptionHandler`, `Flash` y `templates/error/general.html`.
6. `application*.properties`: se quitó la configuración de Thymeleaf; se agregó `app.cors.allowed-origins`; la cookie de sesión de `prod` admite `SESSION_COOKIE_SAME_SITE`.
7. **Seguridad de credenciales:** `application.properties` tenía `spring.datasource.username=postgres` y `spring.datasource.password=admin` escritos en el código, lo que contradecía el README. Ahora usa `${DB_USER:postgres}` y `${DB_PASSWORD}` (sin valor por defecto). Como esa contraseña estuvo en un repositorio público, conviene cambiarla en cualquier PostgreSQL donde se haya usado.
8. `.env.example` ampliado con `CORS_ALLOWED_ORIGINS` y `SESSION_COOKIE_SAME_SITE`; README del backend actualizado.

**Sin cambios (conservado tal cual):** entidades, relaciones, validaciones (`@NotBlank`, `@Email`, `@Size`…), servicios, repositorios, mappers, auditoría, seeders, `PageResponse`, migraciones Flyway `V1` y `V2`, y las pruebas existentes.

**Frontend:** proyecto nuevo (ver arriba). Las pantallas `LoginPage` y `HomePage` no existían como plantillas: son el mínimo imprescindible para poder autenticarse y comprobar la sesión; `HomePage` es provisional (muestra usuario y roles, y permite cerrar sesión).

## 4. Pruebas y verificación

| Qué | Resultado |
|---|---|
| Frontend: `npm install` | OK (sin vulnerabilidades reportadas) |
| Frontend: `npm run build` (`tsc -b` + `vite build`) | **OK** |
| Frontend: `vite` en modo desarrollo sirve `index.html` | OK |
| Frontend: proxy `/api` hacia el backend | **No verificado**: la prueba contra un servidor simulado devolvió 502 porque el simulado ya había terminado |
| Frontend: flujo de login en navegador | **No verificado** (no hay navegador ni backend en el entorno de trabajo) |
| Backend: compilación y pruebas | **No ejecutadas.** El entorno donde se hizo este trabajo no puede descargar de Maven Central, así que `./mvnw` no puede resolver dependencias. El código del backend **no fue compilado** |
| Backend: prueba nueva `UsuarioSesionDtoTest` | Escrita, **sin ejecutar** |

**Riesgo concreto del backend:** el proyecto usa Spring Boot 4.1 / Spring Security 7, y las clases nuevas de seguridad (`SecurityConfig`, `CsrfCookieFilter`) se escribieron sin poder compilarlas. Antes de seguir, ejecutar en tu máquina `cd backend && ./mvnw clean compile && ./mvnw test -Dtest="*Test"` y revisar el flujo completo de login (ver pendientes). Si algo no compila, lo esperable es un import o una firma de Spring Security 7 en esas dos clases.

## 5. Tareas pendientes

1. **Compilar y probar el backend** (comando arriba) y arrancarlo contra PostgreSQL.
2. **Probar de punta a punta** el login desde el navegador: `GET /api/auth/me` (401 + cookie `XSRF-TOKEN`), `POST /api/auth/login`, `GET /api/auth/me` (200), `POST /api/auth/logout`.
3. **CSRF entre dominios distintos:** si frontend y API se publican en dominios sin relación, el frontend no podrá leer la cookie `XSRF-TOKEN`. La solución recomendada es servir ambos bajo un mismo dominio con proxy inverso; la alternativa sería entregar el token desde un endpoint. No se implementó porque excede esta fase.
4. Pruebas automatizadas: de integración para la seguridad (`@WebMvcTest`/`MockMvc`) y del frontend (Vitest + Testing Library).
5. Recuperar/restablecer contraseña: el backend tiene la entidad `TokenRecuperacion` y la tabla, pero nunca tuvo servicio ni pantallas. No se creó nada; las rutas `/recuperar` y `/restablecer` que antes se permitían en `SecurityConfig` se retiraron porque no existían.
6. Gestión de usuarios, roles y permisos: existen los servicios, pero no había controllers ni pantallas. Pertenece a la Fase 1 (parte visual) y siguientes.
7. Layout general, navegación, modo oscuro y los módulos de los casos de uso del diagrama (catálogo, movimientos de inventario, compras, administración, reportes y consulta): fases posteriores.
8. Despliegue (Docker, CI) y `ENV` de producción del frontend: no solicitado en esta fase.
9. Revisar y cambiar la contraseña `admin` que estuvo en `application.properties` (punto 7 de cambios).

## 6. Avance de la Fase 2 — Backend

**Implementado:**
- Se agregaron migraciones Flyway de catálogo (V10), inventario (V20), compras (V30) y unidades de moto (V40), con claves, índices y restricciones de integridad para PostgreSQL.
- Se implementó el dominio, repositorios y API REST de categorías, marcas y productos, incluyendo compatibilidades con modelos de moto.
- CRUD de catálogo, validaciones de nombres/códigos únicos, precios, stock mínimo y eliminación lógica mediante servicios transaccionales.
- Los permisos granulares de catálogo, inventario y compras se incorporaron al seeder; se asignaron permisos a los roles ALMACENERO, CONTADOR y CONSULTA según el principio de menor privilegio.
- Se agregó una prueba de reglas de negocio del catálogo.

**Endpoints incorporados:**
- `GET/POST /api/catalogo/categorias`
- `PUT/DELETE /api/catalogo/categorias/{id}`
- `GET/POST /api/catalogo/marcas`
- `PUT/DELETE /api/catalogo/marcas/{id}`
- `GET/POST /api/catalogo/productos`
- `GET/PUT/DELETE /api/catalogo/productos/{id}`

**Pruebas y comprobaciones:**
- Resultado actualizado: compilación Maven correcta y **48 pruebas aprobadas, 0 fallos**, con `./mvnw test -Dtest="*Test"`.
- La compilación de producción se verificó con `./mvnw -DskipTests compile`.
- No fue posible validar las migraciones ni arrancar la API contra PostgreSQL en este entorno; queda indicado como comprobación pendiente en la máquina con la base configurada.

**Pendientes derivados del alcance:**
- Completar entidades, servicios y controladores de almacenes, stock, kardex, transferencias, conteo físico, ajustes, proveedores, órdenes, recepciones y movimientos de unidad/moto.
- Añadir pruebas MockMvc de autorización por rol, validación y respuestas 401/403 para los nuevos endpoints.
- Ejecutar y validar las migraciones V10–V40 contra PostgreSQL real; las migraciones existentes con entidades no forman parte de un entorno de prueba sin base de datos.
- Completar reportes PDF/Excel y KPIs de valorización una vez estén disponibles stock, kardex y compras.
- Mantener el frontend sin cambios estructurales; cualquier consumo nuevo debe adaptarse al contrato REST existente.

## 7. Avance de la Fase 3 — Frontend

**Implementado:**
- Navegación independiente con barra lateral adaptable, panel principal y ruta protegida `/catalogo`.
- Interfaz real conectada a los endpoints del catálogo implementados en el backend: categorías, marcas y productos.
- Listados, estados de carga, estados vacíos, errores, formularios con validación HTML y confirmaciones de eliminación.
- Visibilidad de rutas y acciones según roles/permisos (`ADMIN`, `PRODUCTO_EDITAR`, `PRODUCTO_VER`).
- Actualización dinámica después de altas y eliminaciones, sin operaciones simuladas.
- Conservado el cliente API con sesiones, cookies, CSRF y `VITE_API_BASE_URL`.

**Conexiones comprobadas por contrato:**
- `GET/POST/PUT/DELETE /api/catalogo/categorias`
- `GET/POST/PUT/DELETE /api/catalogo/marcas`
- `GET/POST/PUT/DELETE /api/catalogo/productos`

**Comprobaciones:**
- `npm install`: 47 paquetes instalados, 0 vulnerabilidades reportadas.
- `npm run build`: compilación TypeScript y build Vite correctos.

**Pendientes/dependencias reales:**
- Inventario, movimientos, conteo físico, ajustes, transferencias, compras, proveedores, recepciones, unidades de moto y reportes requieren endpoints backend aún no implementados; el frontend solo informa la dependencia y no muestra acciones ficticias.
- Faltan pruebas frontend automatizadas.
- Validación visual y de punta a punta requiere backend activo contra PostgreSQL y navegador disponible.
