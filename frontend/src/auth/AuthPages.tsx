import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { useState } from 'react'
import { z } from 'zod'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { restablecerPassword, solicitarRecuperacion } from '../api/auth'
import { ApiError, hayTokenCsrf } from '../api/client'
import { consultarSesion } from '../api/auth'
import { Logo } from '../components/Logo'
import { useAuth } from './AuthContext'

const loginSchema = z.object({ email: z.string().min(1, 'Ingresa tu usuario o correo.'), password: z.string().min(1, 'Ingresa tu contraseña.') })
const emailSchema = z.object({ email: z.string().email('Ingresa un correo válido.') })
const resetSchema = z.object({ password: z.string().min(8, 'La contraseña debe tener al menos 8 caracteres.'), confirmacion: z.string() }).refine((value) => value.password === value.confirmacion, { path: ['confirmacion'], message: 'Las contraseñas no coinciden.' })
type LoginValues = z.infer<typeof loginSchema>
type EmailValues = z.infer<typeof emailSchema>
type ResetValues = z.infer<typeof resetSchema>

function AuthBrand() {
  return <section className="auth-brand"><div className="brand"><Logo /><b>Moto Boutique</b></div>
    <div className="auth-copy"><small>CONTROL TOTAL, DECISIONES CLARAS</small>
      <h1>Tu inventario,<br />siempre en marcha.</h1>
      <p>Gestiona repuestos, movimientos y finanzas desde un solo lugar seguro.</p></div>
    <div className="brand-proof"><span>✓</span><p><b>Plataforma interna segura</b>
      <small>Acceso exclusivo para colaboradores</small></p></div></section>
}

export function LoginPage() {
  const { usuario, login } = useAuth()
  const navigate = useNavigate()
  const location = useSearchParams()[0]
  const form = useForm<LoginValues>({ resolver: zodResolver(loginSchema) })
  // "/" resuelve el dashboard que corresponde al rol (RedirectAfterLogin en App.tsx).
  if (usuario) return <Navigate to="/" replace />
  const submit = form.handleSubmit(async ({ email, password }) => {
    try {
      await login(email, password)
      navigate((location.get('next') || '/'), { replace: true })
    } catch (error) {
      form.setError('root', { message: error instanceof ApiError ? error.detalle : 'No se pudo iniciar sesión.' })
    }
  })
  return <main className="auth" data-page="Auth"><AuthBrand />
    <section className="auth-form"><form className="login-card" onSubmit={(event) => void submit(event)}>
    <div className="mobile-brand"><Logo /><b>Moto Boutique</b></div><small>BIENVENIDO DE NUEVO</small>
    <h2>Inicia sesión</h2><p>Ingresa tus credenciales para continuar.</p>
    <label>Usuario o correo corporativo<input autoComplete="username" {...form.register('email')} /></label>
    {form.formState.errors.email && <small role="alert">{form.formState.errors.email.message}</small>}
    <label>Contraseña<input type="password" autoComplete="current-password" {...form.register('password')} /></label>
    {form.formState.errors.password && <small role="alert">{form.formState.errors.password.message}</small>}
    {form.formState.errors.root && <p className="login-error" role="alert">{form.formState.errors.root.message}</p>}
    <button className="btn primary" disabled={form.formState.isSubmitting}>{form.formState.isSubmitting ? 'Ingresando…' : 'Ingresar al sistema'}</button>
    <Link className="link" to="/recuperar-password">¿Olvidaste tu contraseña?</Link>
    <small className="secure">Acceso protegido · Moto Boutique</small>
  </form></section></main>
}

export function RecuperarPage() {
  const form = useForm<EmailValues>({ resolver: zodResolver(emailSchema) })
  const [enviado, setEnviado] = useState(false)
  const submit = form.handleSubmit(async ({ email }) => {
    try {
      if (!hayTokenCsrf()) await consultarSesion().catch(() => undefined)
      await solicitarRecuperacion(email)
      setEnviado(true)
    } catch (error) {
      form.setError('root', { message: error instanceof ApiError ? error.detalle : 'No se pudo solicitar la recuperación.' })
    }
  })
  return <main className="auth"><AuthBrand /><section className="auth-form"><form className="login-card" onSubmit={(event) => void submit(event)}>
    <small>RECUPERACIÓN DE CUENTA</small><h2>Recuperar acceso</h2>
    <p>Si el correo está registrado, recibirás instrucciones para restablecer tu contraseña.</p>
    <label>Correo corporativo<input type="email" autoComplete="email" {...form.register('email')} /></label>
    {form.formState.errors.email && <small role="alert">{form.formState.errors.email.message}</small>}
    {form.formState.errors.root && <p role="alert" className="login-error">{form.formState.errors.root.message}</p>}
    {enviado && <p role="status">Solicitud enviada.</p>}
    <button className="btn primary" disabled={form.formState.isSubmitting}>Enviar instrucciones</button>
    <Link className="link" to="/login">Volver al inicio de sesión</Link>
  </form></section></main>
}

export function RestablecerPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const token = params.get('token') ?? ''
  const form = useForm<ResetValues>({ resolver: zodResolver(resetSchema) })
  const submit = form.handleSubmit(async ({ password }) => {
    try {
      if (!token) throw new Error('El enlace de recuperación no contiene un token.')
      if (!hayTokenCsrf()) await consultarSesion().catch(() => undefined)
      await restablecerPassword(token, password)
      toast.success('Contraseña actualizada.')
      navigate('/login', { replace: true })
    } catch (error) {
      form.setError('root', { message: error instanceof ApiError ? error.detalle : error instanceof Error ? error.message : 'No se pudo restablecer la contraseña.' })
    }
  })
  return <main className="auth"><AuthBrand /><section className="auth-form"><form className="login-card" onSubmit={(event) => void submit(event)}>
    <small>SEGURIDAD</small><h2>Restablecer contraseña</h2>
    <label>Nueva contraseña<input type="password" autoComplete="new-password" {...form.register('password')} /></label>
    {form.formState.errors.password && <small role="alert">{form.formState.errors.password.message}</small>}
    <label>Confirmar contraseña<input type="password" autoComplete="new-password" {...form.register('confirmacion')} /></label>
    {form.formState.errors.confirmacion && <small role="alert">{form.formState.errors.confirmacion.message}</small>}
    {form.formState.errors.root && <p role="alert" className="login-error">{form.formState.errors.root.message}</p>}
    <button className="btn primary" disabled={form.formState.isSubmitting}>Guardar contraseña</button>
  </form></section></main>
}
