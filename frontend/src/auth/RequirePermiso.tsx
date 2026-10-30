import { Navigate, Outlet, useOutletContext } from 'react-router-dom'
import { useAuth } from './AuthContext'

export function RequirePermiso({ permiso }: { permiso: string }) {
  const { tienePermiso } = useAuth()
  const contexto = useOutletContext()
  return tienePermiso(permiso) ? <Outlet context={contexto} /> : <Navigate to="/403" replace />
}
