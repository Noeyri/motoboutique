# Moto Boutique

Sistema interno de **control de inventario** para una tienda de motos y accesorios (Perú). El proyecto está separado en dos aplicaciones independientes:

| Carpeta | Qué es | Tecnología |
|---|---|---|
| [`backend/`](backend) | API REST | Java 21, Spring Boot 4.1, Spring Security, Spring Data JPA, Flyway, Maven, PostgreSQL |
| [`frontend/`](frontend) | Aplicación web (SPA) | React, TypeScript, Vite, Tailwind CSS |

Para el detalle de la arquitectura, los cambios, las pruebas y lo pendiente, lee [`PROGRESS.md`](PROGRESS.md).

## Requisitos

- JDK 21
- PostgreSQL 16 o superior
- Node.js 20.19 o superior (o 22.12+) y npm
- Git (en Windows puedes usar PowerShell, CMD o Git Bash)

## Ejecutar en local

Necesitas dos terminales: una para el backend y otra para el frontend.

### 1. Backend (API en el puerto 8081)

1. Crea en PostgreSQL una base de datos **vacía** llamada `motoboutique` (minúsculas, codificación UTF8).
2. Entra a `backend` y crea el archivo de entorno:

   ```bash
   cd backend
   cp .env.example .env
   ```

   En PowerShell, usa `Copy-Item .env.example .env` en lugar de `cp`.

3. Edita `.env` y completa al menos `DB_PASSWORD`, `ADMIN_PASSWORD` (8 caracteres o más, sin `\`) y, si quieres usuarios de prueba, `DEMO_PASSWORD`.
4. Arranca:

   ```bash
   # Git Bash
   ./mvnw spring-boot:run
   ```

   En PowerShell o CMD, también desde `backend`, ejecuta `.\mvnw.cmd spring-boot:run`.

   Flyway crea las tablas y el `DataSeeder` carga permisos, roles base y usuarios. Se puede repetir sin duplicar datos.

### 2. Frontend (puerto 5173)

```bash
cd frontend
cp .env.example .env     # opcional: los valores por defecto sirven en desarrollo
npm install
npm run dev
```

Abre <http://localhost:5173> e inicia sesión con `ADMIN_EMAIL` y `ADMIN_PASSWORD` del `.env` del backend. En desarrollo, Vite reenvía las llamadas `/api` al backend (`VITE_DEV_PROXY_TARGET`, por defecto `http://localhost:8081`), de modo que el navegador ve un solo origen y no hace falta configurar CORS.

## Compilar

```bash
# Backend: JAR ejecutable en backend/target
cd backend && ./mvnw clean package

# Frontend: archivos estáticos en frontend/dist
cd frontend && npm run build
```

## Pruebas

```bash
cd backend && ./mvnw test -Dtest="*Test"   # pruebas unitarias, sin base de datos
```

El frontend todavía no tiene pruebas automatizadas (ver `PROGRESS.md`); `npm run build` ejecuta la verificación de tipos de TypeScript.

## Variables de entorno

**Backend** (`backend/.env`, ver `backend/.env.example`): `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `DEMO_PASSWORD`, `CORS_ALLOWED_ORIGINS`, `RESEND_API_KEY`, `EMAIL_FROM`, `FRONTEND_URL` y `PASSWORD_RESET_TOKEN_TTL_MINUTES` (opcional, 30 por defecto). En producción también se configura `SESSION_COOKIE_SAME_SITE` cuando el frontend y la API están en sitios distintos.

**Frontend** (`frontend/.env`, ver `frontend/.env.example`): `VITE_API_BASE_URL` (vacío en desarrollo) y `VITE_DEV_PROXY_TARGET`.

Los archivos `.env` no se suben a Git. Los `.env.example` no contienen credenciales reales.

El correo de recuperación se envía por la API HTTPS de Resend tanto en local como en producción. Configura `RESEND_API_KEY` y un `EMAIL_FROM` admitido por tu cuenta; no se registra el enlace de recuperación en los logs. El remitente de prueba `onboarding@resend.dev` puede estar limitado a destinatarios autorizados por Resend.
Si Resend falla, la API responde HTTP 503; esta política operativa puede revelar si la dirección está registrada durante una falla del proveedor.

## Desplegar en Render

El repositorio incluye [`render.yaml`](render.yaml), que crea el backend como servicio Docker y el frontend como sitio estático. La base de datos permanece en Supabase.

1. **Prepara PostgreSQL en Supabase.** En el proyecto abre **Connect** y usa el endpoint **Session pooler** para un backend persistente en Render. Copia exactamente host, puerto, usuario y base de datos que muestra Supabase; no uses la contraseña de conexión dentro de Git.
2. **Conecta el repositorio a Render.** En Render elige **New > Blueprint**, conecta este repositorio y acepta `render.yaml`. El Dockerfile del backend está en `backend/Dockerfile`.
3. **Completa las variables que Render solicita para `motoboutique-api`:**

   | Variable | Valor |
   |---|---|
   | `DB_HOST` | Host del Session pooler de Supabase |
   | `DB_PORT` | Puerto indicado por Supabase para Session pooler |
   | `DB_NAME` | Nombre de la base indicado por Supabase |
   | `DB_USER` | Usuario completo indicado por Supabase |
   | `DB_PASSWORD` | Contraseña de PostgreSQL de Supabase |
   | `ADMIN_EMAIL` | Correo que usarás para iniciar sesión como administrador |
   | `ADMIN_PASSWORD` | Contraseña fuerte y única para el administrador |
   | `CORS_ALLOWED_ORIGINS` | `https://` más el dominio público del frontend, sin `/` final |
   | `FRONTEND_URL` | El mismo dominio público del frontend |
   | `RESEND_API_KEY` | Clave privada de Resend (solo si habilitarás recuperación por correo) |
   | `EMAIL_FROM` | Remitente permitido en Resend (solo si habilitarás recuperación por correo) |

   `SPRING_PROFILES_ACTIVE=prod`, `SESSION_COOKIE_SAME_SITE=none` y `PASSWORD_RESET_TOKEN_TTL_MINUTES=30` ya quedan declaradas en el Blueprint. Para recuperación por correo, crea/verifica el remitente en Resend y define `RESEND_API_KEY` y `EMAIL_FROM`; sin estas variables, el resto del sistema funciona pero el envío de correos no. No configures `DEMO_PASSWORD`: en producción no se crean usuarios demo. Flyway aplicará las migraciones al arrancar.
4. **Completa la variable de `motoboutique-frontend`:** `VITE_API_BASE_URL` debe ser la URL pública del backend, por ejemplo `https://motoboutique-api.onrender.com` (sin `/api` al final). Las variables `VITE_*` se incorporan al compilar: si las cambias, vuelve a desplegar el frontend.
5. **Verifica los dominios reales.** Si Render asignó nombres distintos, actualiza `CORS_ALLOWED_ORIGINS` y `FRONTEND_URL` en el backend con el origen exacto del frontend, y `VITE_API_BASE_URL` en el frontend con la URL del backend. Guarda los cambios y espera a que ambos servicios terminen el despliegue.
6. **Comprueba los logs del backend.** Confirma que conectó con PostgreSQL, que Flyway terminó sin errores y que creó el administrador. Luego abre la URL del frontend e inicia sesión con `ADMIN_EMAIL` y `ADMIN_PASSWORD`.
7. **Prueba la sesión y una operación protegida.** La API usa cookies de sesión y CSRF. El frontend debe enviar credenciales; la API expone el token CSRF en una cabecera para que funcione entre los dos dominios de Render. No pongas contraseñas ni claves en variables `VITE_*`.

Para habilitar recuperación de contraseña configura también `RESEND_API_KEY` y `EMAIL_FROM` en el backend, con un remitente admitido por Resend. Mantén todas las credenciales solo en **Environment** del servicio privado en Render; no compartas ni subas `backend/.env`. El servicio gratuito de Render puede detenerse por inactividad, así que la primera solicitud puede tardar en responder.

Algunos navegadores bloquean cookies en solicitudes entre sitios. Para una sesión más confiable en producción, asigna dominios propios al frontend y a la API bajo el mismo dominio base (por ejemplo, `app.tudominio.pe` y `api.tudominio.pe`).

## Conectar a Supabase

Supabase proporciona PostgreSQL compatible con el backend. Para usarla como base de datos:

1. Crea un proyecto nuevo en https://supabase.com/dashboard.
2. En **Project Settings > Database**, copia el host PostgreSQL, puerto, usuario, nombre de base y contraseña.
3. Edita `backend/.env` con esos datos:

   ```properties
   DB_HOST=db.TU-PROYECTO.supabase.co
   DB_PORT=5432
   DB_NAME=postgres
   DB_USER=postgres
   DB_PASSWORD=TU_CONTRASENA
   ```

4. Arranca el backend. Flyway crea o actualiza las tablas automáticamente, incluida `estado_aplicacion` para los datos editables del frontend.
5. Si la contraseña contiene caracteres especiales, usa su codificación URL cuando corresponda.
6. Para producción configura también el host permitido del frontend:

   ```properties
   CORS_ALLOWED_ORIGINS=https://tu-frontend.tudominio.com
   ```

> Supabase es solo la base de datos PostgreSQL. El frontend debe seguir enviando las peticiones al backend; no se recomienda exponer directamente la clave `service_role` ni las tablas desde el navegador.

### Conexión obligatoria del backend

El backend debe usar el rol PostgreSQL `postgres` (no `anon`, ni el usuario de la API REST y no la clave `service_role`). PostgreSQL omite las políticas RLS para ese rol, por lo que el backend puede ejecutar las migraciones y JPA, mientras que las tablas permanecen protegidas frente a la API REST de Supabase. Todas las tablas públicas tienen RLS habilitado desde la migración V55.

`6543` es el puerto del transaction pooler de Supabase. Flyway y JPA pueden perder sesiones, conexiones preparadas o funciones transaccionales. Configura `DB_PORT=5432`, usando el session pooler o la conexión directa, y evita cargar archivos `.env` con un puerto distinto según el entorno.

## Publicar frontend y backend

Lo más simple es servir ambos bajo el mismo dominio (por ejemplo un proxy inverso que reenvíe `/api` al backend). Si los publicas en dominios distintos, define `VITE_API_BASE_URL` al compilar el frontend, agrega su origen a `CORS_ALLOWED_ORIGINS` y usa `SESSION_COOKIE_SAME_SITE=none` (requiere HTTPS). Ten en cuenta que, entre dominios sin relación, el navegador no deja que el frontend lea la cookie del token CSRF; ver "Tareas pendientes" en `PROGRESS.md`.

### Configurar el correo en Render

Las variables de correo están incluidas en `render.yaml`. En el backend configura `RESEND_API_KEY` con la clave secreta de Resend y `EMAIL_FROM` con un remitente permitido por tu cuenta; `FRONTEND_URL` debe apuntar al dominio público del frontend. El tiempo de expiración queda en 30 minutos por defecto mediante `PASSWORD_RESET_TOKEN_TTL_MINUTES`. No se necesita configuración SMTP (`SPRING_MAIL_*`): esta aplicación envía el correo mediante la API HTTPS de Resend. No guardes la clave en Git ni en variables `VITE_*`. Para producción, verifica el dominio remitente en Resend y publica los registros DNS solicitados, incluidos SPF y DKIM. El remitente de prueba puede limitar los destinatarios permitidos.
