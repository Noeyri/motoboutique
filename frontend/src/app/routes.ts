
import type { ReactNode } from 'react'

export interface RutaNavegacion {
  label: string
  path: string
  permiso: string
  grupo?: string
  elemento?: ReactNode
}

export const rutas: RutaNavegacion[] = [
  { label: 'Dashboard', path: '/admin/dashboard', permiso: 'USUARIO_VER' },
  { label: 'Usuarios', path: '/admin/usuarios', permiso: 'USUARIO_VER' },
  { label: 'Roles y permisos', path: '/admin/roles', permiso: 'ROL_VER' },
  { label: 'Configuración', path: '/admin/configuracion', permiso: 'CONFIG_VER' },

  { label: 'Resumen de almacén', path: '/almacen/dashboard', permiso: 'INVENTARIO_VER' },
  { label: 'Movimientos', path: '/almacen/movimientos', permiso: 'INVENTARIO_VER', grupo: 'MOVIMIENTOS' },
  { label: 'Entradas', path: '/almacen/entradas', permiso: 'INVENTARIO_EDITAR', grupo: 'MOVIMIENTOS' },
  { label: 'Salidas', path: '/almacen/salidas', permiso: 'INVENTARIO_EDITAR', grupo: 'MOVIMIENTOS' },
  { label: 'Conteo físico', path: '/almacen/conteo-fisico', permiso: 'CONTEO_EDITAR', grupo: 'MOVIMIENTOS' },
  { label: 'Ajustes', path: '/almacen/ajustes', permiso: 'AJUSTE_SOLICITAR', grupo: 'MOVIMIENTOS' },

  { label: 'Productos', path: '/catalogo/productos', permiso: 'PRODUCTO_VER', grupo: 'CATÁLOGO' },
  { label: 'Importación', path: '/catalogo/importacion', permiso: 'IMPORTAR_CATALOGO', grupo: 'CATÁLOGO' },

  { label: 'Resumen financiero', path: '/finanzas/dashboard', permiso: 'REPORTE_VER' },
  { label: 'Valorización', path: '/finanzas/valorizacion', permiso: 'REPORTE_VER' },
  { label: 'Kardex', path: '/finanzas/kardex', permiso: 'KARDEX_VER' },
  { label: 'Reportes', path: '/finanzas/reportes', permiso: 'REPORTE_VER' },

  { label: 'Stock', path: '/consulta/stock', permiso: 'INVENTARIO_VER' },
  { label: 'Compatibilidades', path: '/consulta/compatibilidades', permiso: 'PRODUCTO_VER' },
  { label: 'Catálogo', path: '/consulta/catalogo', permiso: 'PRODUCTO_VER' },
]

export function rutasPermitidas(
  tienePermiso: (permiso: string) => boolean
) {
  return rutas.filter((ruta) => tienePermiso(ruta.permiso))
}
