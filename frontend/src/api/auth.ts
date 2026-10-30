import { apiFetch } from './client'
import type { UsuarioSesion } from './types'

/** GET /api/auth/me. Sin sesión lanza ApiError 401 (y de paso entrega la cookie del token CSRF). */
export function consultarSesion(): Promise<UsuarioSesion> {
  return apiFetch<UsuarioSesion>('/api/auth/me', { notificarNoAutorizado: false })
}

/** POST /api/auth/login (campos "email" y "password", como el formulario original). */
export function iniciarSesion(email: string, password: string): Promise<void> {
  return apiFetch<void>('/api/auth/login', { method: 'POST', formulario: { email, password } })
}

/** POST /api/auth/logout. */
export function cerrarSesion(): Promise<void> {
  return apiFetch<void>('/api/auth/logout', { method: 'POST' })
}

export function solicitarRecuperacion(email: string): Promise<{ mensaje: string }> {
  return apiFetch<{ mensaje: string }>('/api/auth/recuperar', {
    method: 'POST',
    json: { email },
  })
}

export function restablecerPassword(token: string, password: string): Promise<void> {
  return apiFetch<void>('/api/auth/restablecer', {
    method: 'POST',
    json: { token, password },
  })
}
