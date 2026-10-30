import type { ReactNode } from 'react'

export interface RutaNavegacion {
  label: string
  path: string
  permiso: string
  grupo?: string
  // false: la ruta existe pero no se lista en el menú (duplica otra pantalla con el mismo permiso).
  enMenu?: boolean
  elemento?: ReactNode
}

export const rutas: RutaNavegacion[] = [
  { label: 'Dashboard general', path: '/admin/dashboard', permiso: 'USUARIO_VER', grupo: 'ADMINISTRACIÓN' },
  { label: 'Usuarios', path: '/admin/usuarios', permiso: 'USUARIO_VER', grupo: 'ADMINISTRACIÓN' },
  { label: 'Roles y permisos', path: '/admin/roles', permiso: 'ROL_VER', grupo: 'ADMINISTRACIÓN' },
  { label: 'Configuración', path: '/admin/configuracion', permiso: 'CONFIG_VER', grupo: 'ADMINISTRACIÓN' },
  { label: 'Aprobaciones', path: '/admin/aprobaciones', permiso: 'AJUSTE_APROBAR', grupo: 'ADMINISTRACIÓN' },
  { label: 'Dashboard de almacén', path: '/almacen/dashboard', permiso: 'INVENTARIO_VER', grupo: 'ALMACÉN' },
  { label: 'Movimientos', path: '/almacen/movimientos', permiso: 'INVENTARIO_VER', grupo: 'ALMACÉN' },
  { label: 'Entradas', path: '/almacen/entradas', permiso: 'INVENTARIO_EDITAR', grupo: 'ALMACÉN' },
  { label: 'Salidas', path: '/almacen/salidas', permiso: 'INVENTARIO_EDITAR', grupo: 'ALMACÉN' },
  { label: 'Transferencias', path: '/almacen/transferencias', permiso: 'TRANSFERENCIA_EDITAR', grupo: 'ALMACÉN' },
  { label: 'Conteo físico', path: '/almacen/conteo-fisico', permiso: 'CONTEO_EDITAR', grupo: 'ALMACÉN' },
  { label: 'Ajustes', path: '/almacen/ajustes', permiso: 'AJUSTE_SOLICITAR', grupo: 'ALMACÉN' },
  { label: 'Productos', path: '/catalogo/productos', permiso: 'PRODUCTO_VER', grupo: 'CATÁLOGO' },
  { label: 'Motocicletas', path: '/catalogo/motos', permiso: 'PRODUCTO_VER', grupo: 'CATÁLOGO' },
  { label: 'Importación', path: '/catalogo/importacion', permiso: 'IMPORTAR_CATALOGO', grupo: 'CATÁLOGO' },
  { label: 'Proveedores', path: '/compras/proveedores', permiso: 'COMPRA_VER', grupo: 'COMPRAS' },
  { label: 'Órdenes', path: '/compras/ordenes', permiso: 'COMPRA_VER', grupo: 'COMPRAS' },
  { label: 'Recepciones', path: '/compras/recepciones', permiso: 'COMPRA_VER', grupo: 'COMPRAS' },
  { label: 'Dashboard de finanzas', path: '/finanzas/dashboard', permiso: 'REPORTE_VER', grupo: 'FINANZAS' },
  { label: 'Valorización', path: '/finanzas/valorizacion', permiso: 'REPORTE_VER', grupo: 'FINANZAS' },
  { label: 'Kardex', path: '/finanzas/kardex', permiso: 'KARDEX_VER', grupo: 'FINANZAS' },
  { label: 'Reportes', path: '/finanzas/reportes', permiso: 'REPORTE_VER', grupo: 'FINANZAS' },
  { label: 'Dashboard de consulta', path: '/consulta/dashboard', permiso: 'PRODUCTO_VER', grupo: 'CONSULTA' },
  { label: 'Stock', path: '/consulta/stock', permiso: 'INVENTARIO_VER', grupo: 'CONSULTA' },
  // Abren las mismas páginas que Motocicletas y Productos, que ya ve cualquiera con PRODUCTO_VER.
  { label: 'Compatibilidades', path: '/consulta/compatibilidades', permiso: 'PRODUCTO_VER', grupo: 'CONSULTA', enMenu: false },
  { label: 'Catálogo', path: '/consulta/catalogo', permiso: 'PRODUCTO_VER', grupo: 'CONSULTA', enMenu: false },
]

export function rutasPermitidas(tienePermiso: (permiso: string) => boolean) {
  return rutas.filter((ruta) => tienePermiso(ruta.permiso))
}
