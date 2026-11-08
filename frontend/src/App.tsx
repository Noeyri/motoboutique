import { lazy, Suspense, useEffect } from 'react'
import { Navigate, Route, Routes, useNavigate } from 'react-router-dom'
import { AppShell } from './layout/AppShell'
import { LoginPage, RecuperarPage, RestablecerPage } from './auth/AuthPages'
import { RequireAuth } from './auth/RequireAuth'
import { RequirePermiso } from './auth/RequirePermiso'
import { useAuth } from './auth/AuthContext'
import { rutas } from './app/routes'
import { Skeleton } from './components/Skeleton'
import { PlaceholderPage } from './features/PlaceholderPage'
import { EmptyState } from './components/EmptyState'

const MovimientosPage = lazy(() => import('./features/inventario/MovimientosPage')
  .then((module) => ({ default: module.MovimientosPage })))
const MovimientoFormPage = lazy(() => import('./features/inventario/MovimientoFormPage')
  .then((module) => ({ default: module.MovimientoFormPage })))
const TransferenciasPage = lazy(() => import('./features/inventario/TransferenciasPage')
  .then((module) => ({ default: module.TransferenciasPage })))
const ConteoFisicoPage = lazy(() => import('./features/inventario/ConteoFisicoPage')
  .then((module) => ({ default: module.ConteoFisicoPage })))
const AjustesPage = lazy(() => import('./features/inventario/AjustesPage')
  .then((module) => ({ default: module.AjustesPage })))
const ProductosPage = lazy(() => import('./features/catalogo/ProductosPage')
  .then((module) => ({ default: module.ProductosPage })))
const MotocicletasPage = lazy(() => import('./features/catalogo/MotocicletasPage')
  .then((module) => ({ default: module.MotocicletasPage })))
const ImportacionPage = lazy(() => import('./features/catalogo/ImportacionPage')
  .then((module) => ({ default: module.ImportacionPage })))
const ComprasPage = lazy(() => import('./features/compras/ComprasPage')
  .then((module) => ({ default: module.ComprasPage })))
const StockPage = lazy(() => import('./features/inventario/StockPage')
  .then((module) => ({ default: module.StockPage })))

const DashboardPage = lazy(() => import('./features/dashboard/DashboardPage')
  .then((modulo) => ({ default: modulo.DashboardPage })))
const FinanzasDashboardPage = lazy(() => import('./features/dashboard/FinanzasDashboardPage')
  .then((modulo) => ({ default: modulo.FinanzasDashboardPage })))
const UsuariosPage = lazy(() => import('./features/seguridad/UsuariosPage')
  .then((modulo) => ({ default: modulo.UsuariosPage })))
const RoleMatrix = lazy(() => import('./features/seguridad/RoleMatrix')
  .then((modulo) => ({ default: modulo.RoleMatrix })))
const ConfiguracionEmpresaForm = lazy(() => import('./features/configuracion/ConfiguracionEmpresaForm')
  .then((modulo) => ({ default: modulo.ConfiguracionEmpresaForm })))
const AprobacionesPage = lazy(() => import('./features/seguridad/AprobacionesPage')
  .then((modulo) => ({ default: modulo.AprobacionesPage })))
const ValorizacionPage = lazy(() => import('./features/finanzas/ValorizacionPage')
  .then((modulo) => ({ default: modulo.ValorizacionPage })))
const KardexPage = lazy(() => import('./features/finanzas/KardexPage')
  .then((modulo) => ({ default: modulo.KardexPage })))
const ReportesPage = lazy(() => import('./features/finanzas/ReportesPage')
  .then((modulo) => ({ default: modulo.ReportesPage })))

// Dashboard propio de cada rol base. Va primero porque los permisos se solapan
// (CONSULTA también tiene INVENTARIO_VER y acabaría en el dashboard del almacén).
const DASHBOARD_POR_ROL: Array<[string, string]> = [
  ['ADMIN', '/admin/dashboard'],
  ['CONTADOR', '/finanzas/dashboard'],
  ['ALMACENERO', '/almacen/dashboard'],
  ['CONSULTA', '/consulta/dashboard'],
]

function RedirectAfterLogin() {
  const { tienePermiso, tieneRol } = useAuth()
  const permitido = (path: string) => {
    const ruta = rutas.find((item) => item.path === path)
    return ruta !== undefined && tienePermiso(ruta.permiso)
  }
  const dashboardPath = DASHBOARD_POR_ROL
    .find(([rol, path]) => tieneRol(rol) && permitido(path))?.[1]
    ?? ['/admin/dashboard', '/finanzas/dashboard', '/almacen/dashboard', '/consulta/dashboard'].find(permitido)
  const path = dashboardPath ?? rutas.find((ruta) => tienePermiso(ruta.permiso))?.path
  return <Navigate to={path ?? '/403'} replace />
}

function UnauthorizedRedirect() {
  const navigate = useNavigate()
  useEffect(() => {
    const handler = () => navigate('/login', { replace: true })
    window.addEventListener('api:unauthorized', handler)
    return () => window.removeEventListener('api:unauthorized', handler)
  }, [navigate])
  return null
}

function RutaContenido({ path }: { path: string }) {
  if (path === '/finanzas/dashboard') return <FinanzasDashboardPage />
  if (path === '/consulta/dashboard') return <DashboardPage />
  if (path.endsWith('/dashboard')) return <DashboardPage />
  if (path === '/admin/usuarios') return <UsuariosPage />
  if (path === '/admin/roles') return <RoleMatrix />
  if (path === '/admin/configuracion') return <ConfiguracionEmpresaForm />
  if (path === '/admin/aprobaciones') return <AprobacionesPage />
  if (path === '/finanzas/valorizacion') return <ValorizacionPage />
  if (path === '/finanzas/kardex') return <KardexPage />
  if (path === '/finanzas/reportes') return <ReportesPage />
  if (path === '/almacen/movimientos') return <MovimientosPage />
  if (path === '/almacen/entradas') return <MovimientoFormPage tipo="entrada" />
  if (path === '/almacen/salidas') return <MovimientoFormPage tipo="salida" />
  if (path === '/almacen/transferencias') return <TransferenciasPage />
  if (path === '/almacen/conteo-fisico') return <ConteoFisicoPage />
  if (path === '/almacen/ajustes') return <AjustesPage />
  if (path === '/catalogo/productos') return <ProductosPage />
  if (path === '/catalogo/motos') return <MotocicletasPage />
  if (path === '/catalogo/importacion') return <ImportacionPage />
  if (path.startsWith('/compras/')) return <ComprasPage />
  if (path === '/consulta/catalogo') return <ProductosPage />
  if (path === '/consulta/compatibilidades') return <MotocicletasPage />
  if (path === '/consulta/stock') return <StockPage />
  return <PlaceholderPage />
}

export default function App() {
  return <>
    <UnauthorizedRedirect />
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/recuperar-password" element={<RecuperarPage />} />
      <Route path="/restablecer-password" element={<RestablecerPage />} />
      <Route path="/403" element={<main><EmptyState title="Acceso denegado" description="No tienes permiso para acceder a esta pantalla." /></main>} />
      <Route path="/404" element={<main><EmptyState title="Página no encontrada" description="La dirección solicitada no existe." /></main>} />
      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          {rutas.map((ruta) => <Route key={ruta.path} element={<RequirePermiso permiso={ruta.permiso} />}>
            <Route path={ruta.path} element={<Suspense fallback={<Skeleton />}><RutaContenido path={ruta.path} /></Suspense>} />
          </Route>)}
          <Route index element={<RedirectAfterLogin />} />
          <Route path="*" element={<Navigate to="/404" replace />} />
        </Route>
      </Route>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="*" element={<Navigate to="/404" replace />} />
    </Routes>
  </>
}
