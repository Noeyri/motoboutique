import { detalleHttp, tituloHttp } from './mensajesHttp'
import type { ProblemDetail } from './types'
import { toast } from 'sonner'

/** Vacío = mismo origen (en desarrollo Vite reenvía /api al backend). */
const BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

const COOKIE_CSRF = 'XSRF-TOKEN'
const CABECERA_CSRF = 'X-XSRF-TOKEN'

/** Error de la API con los datos del ProblemDetail ya normalizados. */
export class ApiError extends Error {
  readonly status: number
  readonly titulo: string
  readonly detalle: string
  readonly errores?: Record<string, string>
  readonly referencia?: string

  constructor(status: number, problema?: ProblemDetail) {
    const detalle = problema?.detail || detalleHttp(status)
    super(detalle)
    this.name = 'ApiError'
    this.status = status
    this.titulo = problema?.title || tituloHttp(status)
    this.detalle = detalle
    this.errores = problema?.errores
    this.referencia = problema?.referencia
  }
}

export function leerCookie(nombre: string): string | null {
  const par = document.cookie.split('; ').find((c) => c.startsWith(`${nombre}=`))
  return par ? decodeURIComponent(par.slice(nombre.length + 1)) : null
}

export function hayTokenCsrf(): boolean {
  return leerCookie(COOKIE_CSRF) !== null
}

interface Opciones {
  method?: string
  /** Evita el evento global al consultar la sesión en pantallas públicas, donde 401 es normal. */
  notificarNoAutorizado?: boolean
  /** Cuerpo JSON (se serializa). */
  json?: unknown
  /** Cuerpo de formulario (application/x-www-form-urlencoded). */
  formulario?: Record<string, string>
  multipart?: FormData
}

export async function apiBlob(ruta: string): Promise<Blob> {
  const cabeceras: Record<string, string> = {}
  const token = leerCookie(COOKIE_CSRF)
  if (token) cabeceras[CABECERA_CSRF] = token
  let respuesta: Response
  try {
    respuesta = await fetch(`${BASE_URL}${ruta}`, {
      headers: cabeceras,
      credentials: 'include',
    })
  } catch {
    throw new ApiError(0, {
      title: 'Sin conexión con el servidor',
      detail: 'No se pudo conectar con el servidor. Verifica que el backend esté en ejecución.',
    })
  }
  if (!respuesta.ok) {
    let problema: ProblemDetail | undefined
    try { problema = await respuesta.json() as ProblemDetail } catch { problema = undefined }
    const error = new ApiError(respuesta.status, problema)
    if (respuesta.status === 401) window.dispatchEvent(new Event('api:unauthorized'))
    else if (respuesta.status === 403) toast.error('No tienes permiso para realizar esta acción.')
    throw error
  }
  return respuesta.blob()
}

export async function apiUpload<T>(ruta: string, multipart: FormData): Promise<T> {
  return apiFetch<T>(ruta, { method: 'POST', multipart })
}

/**
 * Llama a la API con la cookie de sesión y, en las peticiones que modifican datos, el token CSRF.
 * Devuelve undefined en las respuestas 204. Lanza ApiError en cualquier respuesta no exitosa.
 */
export async function apiFetch<T = void>(ruta: string, opciones: Opciones = {}): Promise<T> {
  const metodo = (opciones.method ?? 'GET').toUpperCase()
  const cabeceras: Record<string, string> = { Accept: 'application/json' }
  let cuerpo: BodyInit | undefined

  if (opciones.json !== undefined) {
    cabeceras['Content-Type'] = 'application/json'
    cuerpo = JSON.stringify(opciones.json)
  } else if (opciones.formulario) {
    cabeceras['Content-Type'] = 'application/x-www-form-urlencoded'
    cuerpo = new URLSearchParams(opciones.formulario).toString()
  } else if (opciones.multipart) {
    cuerpo = opciones.multipart
  }

  if (!['GET', 'HEAD', 'OPTIONS'].includes(metodo)) {
    const token = leerCookie(COOKIE_CSRF)
    if (token) cabeceras[CABECERA_CSRF] = token
  }

  let respuesta: Response
  try {
    respuesta = await fetch(`${BASE_URL}${ruta}`, {
      method: metodo,
      headers: cabeceras,
      body: cuerpo,
      credentials: 'include',
    })
  } catch {
    throw new ApiError(0, {
      title: 'Sin conexión con el servidor',
      detail: 'No se pudo conectar con el servidor. Verifica que el backend esté en ejecución.',
    })
  }

  if (!respuesta.ok) {
    let problema: ProblemDetail | undefined
    try {
      problema = (await respuesta.json()) as ProblemDetail
    } catch {
      problema = undefined
    }
    const error = new ApiError(respuesta.status, problema)
    if (respuesta.status === 401 && opciones.notificarNoAutorizado !== false) {
      window.dispatchEvent(new Event('api:unauthorized'))
    } else if (respuesta.status === 403) {
      toast.error('No tienes permiso para realizar esta acción.')
    }
    throw error
  }

  if (respuesta.status === 204) return undefined as T
  return (await respuesta.json()) as T
}
