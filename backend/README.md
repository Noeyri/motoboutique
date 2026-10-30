# Moto Boutique — Backend (API REST)

API REST del sistema interno de **control de inventario** para una tienda de motos lineales, repuestos y accesorios (Perú): catálogo con compatibilidad por modelo de moto, stock por almacén, kardex valorizado, compras a proveedores, motocicletas con chasis y motor, alertas de stock y reportes.

> No es una tienda online ni un punto de venta: no maneja carrito, cobros ni comprobantes.

## Tecnología

- Java 21, Maven (`mvnw`) y Spring Boot 4.1
- Spring MVC (API REST en JSON), Spring Security (sesión + CSRF) y Spring Data JPA
- PostgreSQL con migraciones Flyway (`ddl-auto=validate`: el esquema solo cambia con migraciones)
- Lombok, MapStruct y Bean Validation
- El frontend (React + TypeScript + Vite) vive aparte, en `../frontend`

## Requisitos

- JDK 21
- PostgreSQL 16 o superior
- Git (en Windows puedes usar PowerShell, CMD o Git Bash)

## Puesta en marcha

1. Clonar el repositorio y entrar a la carpeta `backend`:

   ```bash
   cd backend
   ```

2. Crear en PostgreSQL una base de datos **vacía** llamada `motoboutique` (todo en minúsculas, codificación UTF8).
3. Copiar `.env.example` como `.env` y completar los valores (ver la tabla de abajo). El archivo `.env` **nunca se sube a Git**. En PowerShell, usa `Copy-Item .env.example .env`; en CMD, `copy .env.example .env`.
4. Arrancar desde `backend` (el `.env` se lee desde la carpeta actual):

   ```bash
   # Git Bash
   ./mvnw spring-boot:run
   ```

   En PowerShell o CMD, ejecuta `.\mvnw.cmd spring-boot:run`.

5. La API queda en <http://localhost:8081>. Para iniciar sesión usa el frontend (`../frontend`) con el correo `ADMIN_EMAIL` y la contraseña `ADMIN_PASSWORD` que pusiste en el `.env`.

Al arrancar, Flyway crea las tablas y el `DataSeeder` carga los permisos, los roles base y los usuarios. El seeder se puede ejecutar muchas veces sin duplicar datos.

### Variables del archivo `.env`

| Variable | Para qué sirve |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` | Conexión a PostgreSQL (valores por defecto en `.env.example`) |
| `DB_PASSWORD` | Contraseña de tu usuario de PostgreSQL |
| `ADMIN_EMAIL` | Correo del administrador inicial |
| `ADMIN_PASSWORD` | Contraseña del administrador (8 caracteres o más, sin `\`) |
| `DEMO_PASSWORD` | Contraseña común de los usuarios demo (solo perfil `dev`; si queda vacía no se crean) |
| `CORS_ALLOWED_ORIGINS` | Orígenes del frontend autorizados, separados por coma (por defecto `http://localhost:5173`) |
| `SESSION_COOKIE_SAME_SITE` | Solo perfil `prod`: `lax` (por defecto) o `none` si frontend y API están en sitios distintos |
| `RESEND_API_KEY` | Clave privada de la API de Resend; se usa desde el backend |
| `EMAIL_FROM` | Remitente admitido/verificado por Resend (el de prueba puede limitar destinatarios) |
| `FRONTEND_URL` | URL base del frontend para construir el enlace de recuperación |
| `PASSWORD_RESET_TOKEN_TTL_MINUTES` | Duración del enlace en minutos (opcional; 30 por defecto) |

Usuarios demo (perfil `dev`): `almacenero@`, `contador@` y `consulta@` con el dominio `motoboutique.local`.
La recuperación envía correo por la API HTTPS de Resend en todos los perfiles; el enlace y el token no se escriben en logs. Si Resend falla, la solicitud devuelve HTTP 503 con un mensaje sin detalles del proveedor.
Esta respuesta explícita ante fallos es una decisión operativa: como la dirección se busca antes de enviar, un 503 durante un fallo de Resend puede distinguir una cuenta existente de una inexistente. Para evitar esa enumeración se necesitaría una política diferente, por ejemplo procesar el envío de forma asíncrona con una cola/outbox.

### Configurar correo en desarrollo local

1. Desde `backend`, copia `.env.example` a `.env` (en PowerShell: `Copy-Item .env.example .env`).
2. Completa `RESEND_API_KEY` con una clave privada de Resend y deja `EMAIL_FROM=onboarding@resend.dev` para pruebas o usa un remitente verificado. Configura `FRONTEND_URL=http://localhost:5173` y, si quieres, `PASSWORD_RESET_TOKEN_TTL_MINUTES=30`.
3. Arranca el backend desde `backend` con `.\mvnw.cmd spring-boot:run` y el frontend desde `frontend` con `npm run dev`.
4. En `/login`, usa “¿Olvidaste tu contraseña?”, solicita el enlace, abre el correo recibido y define la nueva contraseña en el frontend. Verifica que puedes iniciar sesión con la contraseña nueva y que un enlace ya usado no se pueda reutilizar.

El remitente de pruebas de Resend puede enviar únicamente a destinatarios permitidos por Resend. No copies la clave a `frontend/.env` ni uses una variable `VITE_*`.

### Configurar correo en Render

No hay un Blueprint `render.yaml` en este repositorio. En el dashboard del servicio backend configura `SPRING_PROFILES_ACTIVE=prod`, `RESEND_API_KEY` como secreto, `EMAIL_FROM`, `FRONTEND_URL` con la URL pública del frontend y, si se cambia, `PASSWORD_RESET_TOKEN_TTL_MINUTES`. Configura también las variables existentes de PostgreSQL, `CORS_ALLOWED_ORIGINS` con el origen exacto del frontend y `SESSION_COOKIE_SAME_SITE` según el despliegue. Si frontend y API son servicios separados, configura `VITE_API_BASE_URL` con la URL del backend al compilar el frontend. Para producción, verifica el dominio del remitente en Resend y publica los registros DNS solicitados (SPF y DKIM).

## API disponible en esta fase

| Método y ruta | Descripción |
|---|---|
| `POST /api/auth/login` | Inicia sesión. Formulario `application/x-www-form-urlencoded` con `email` y `password`. Responde 204 o 401 |
| `GET /api/auth/me` | Usuario de la sesión actual (id, nombre, correo, roles y permisos). Sin sesión responde 401 |
| `POST /api/auth/logout` | Cierra la sesión. Responde 204 |
| `POST /api/auth/recuperar` | Solicita instrucciones; respuesta genérica para correos registrados y no registrados |
| `POST /api/auth/restablecer` | Restablece contraseña con token de un solo uso, con vencimiento a 30 minutos |
| `GET /api/roles`, `GET /api/permisos` | Consulta roles y permisos |
| `PUT /api/roles/{id}/permisos` | Actualiza permisos del rol y registra el cambio en auditoría |
| `GET /api/auditoria` | Consulta eventos de auditoría con paginación |
| `GET /api/configuracion`, `PUT /api/configuracion` | Consulta y actualiza la configuración de empresa |
| `/api/inventario/conteos` | Apertura, listado, actualización de conteos físicos y cierre |
| `/api/inventario/ajustes`, `/api/aprobaciones` | Solicitudes, consulta y resolución individual o por lote de ajustes |

Autenticación por sesión (cookie `JSESSIONID`). CSRF activo: la cookie `XSRF-TOKEN` debe reenviarse en la cabecera `X-XSRF-TOKEN` en todo POST/PUT/PATCH/DELETE. Los errores se devuelven como `ProblemDetail` (RFC 9457).

## Pruebas

```bash
./mvnw test -Dtest="*Test"
```

Son pruebas unitarias y no necesitan base de datos. `MotoboutiqueApplicationTests` arranca toda la aplicación, por eso se excluye con ese patrón.

## Estructura

Monolito modular por capas, paquete base `com.motoboutique`:

```
com.motoboutique
 ├─ shared/         config, exception, audit, event, util, web
 ├─ seguridad/      usuarios, roles, permisos, autenticación
 ├─ catalogo/       categorías, marcas, modelos de moto, productos, compatibilidad
 ├─ inventario/     almacenes, stock, kardex, ajustes, transferencias, conteo, alertas
 ├─ compras/        proveedores, órdenes de compra, recepciones
 ├─ motos/          unidades con chasis y motor
 ├─ reportes/       consultas y exportaciones
 ├─ configuracion/  datos del negocio
 └─ seed/           datos iniciales por fases
```

Cada módulo tiene sus capas: `web`, `service`, `repository`, `domain` y `dto`.

### Reglas de arquitectura

1. El **controller** solo maneja HTTP y validación. Cero lógica de negocio.
2. El **service** contiene las reglas y es `@Transactional`. Un módulo usa a otro solo a través de la **interfaz** de su service, nunca de su repository.
3. Las entidades no llegan al cliente: la API devuelve **DTOs** (MapStruct).
4. El stock solo cambia mediante `MovimientoInventarioService`; el kardex nunca se edita ni se borra.
5. Dinero y cantidades con `BigDecimal`; nunca `double`.
6. Toda integración externa va detrás de una interfaz (puerto).
7. Sin `@Data` en entidades JPA: usar `@Getter` y `@Setter`.

## Cómo trabajamos en equipo

- **Ramas:** `main` siempre debe compilar y pasar las pruebas. Cada tarea va en su rama `feature/<modulo>-<tema>` (por ejemplo `feature/catalogo-categorias`) y se une a `main` con un Pull Request revisado por otra persona.
- **Commits:** Conventional Commits en español, por ejemplo `feat(catalogo): agrega el listado de categorías` o `fix(seguridad): corrige el cierre de sesión`.
- **Antes de abrir un Pull Request:** `./mvnw clean compile` y `./mvnw test -Dtest="*Test"` en verde.
- **Migraciones Flyway:** nunca se edita una migración ya aplicada; se crea una nueva. Para que no choquen los números entre compañeros, cada módulo usa un rango de versiones:

  | Módulo | Versiones |
  |---|---|
  | seguridad | V2 a V9 |
  | catálogo | V10 a V19 |
  | inventario | V20 a V29 |
  | compras | V30 a V39 |
  | motos | V40 a V49 |
  | reportes, configuración y otros | V50 en adelante |

- **Secretos:** ninguna contraseña, token ni llave en el repositorio. Todo va en el `.env` local o en variables de entorno. Este repositorio es público.
- **Datos reales del negocio** (costos, listas de precios, clientes): no se suben. Usar datos de ejemplo.

## Estado del proyecto

- Seguridad y administración: usuarios, permisos por rol, configuración de empresa, recuperación de contraseñas y auditoría persistida.
- Inventario: movimientos, transferencias, conteos físicos y aprobaciones de ajustes mediante la vía transaccional de inventario.
- Siguientes fases: catálogo, inventario, compras, motocicletas, dashboard y reportes.