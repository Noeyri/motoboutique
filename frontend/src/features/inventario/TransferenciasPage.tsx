import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { useAuth } from '../../auth/AuthContext'
import { catalogoApi } from '../../api/catalogo'
import { ApiError } from '../../api/client'
import { Badge } from '../../components/Badge'
import { ErrorState } from '../../components/ErrorState'
import { Pagination } from '../../components/Pagination'
import { SearchInput } from '../../components/SearchInput'
import { Skeleton } from '../../components/Skeleton'
import { inventarioApi } from './api'

interface LineaTransferencia { productoId: string; cantidad: string }

export function TransferenciasPage() {
  const [params, setParams] = useSearchParams()
  const { tienePermiso } = useAuth()
  const puedeEditar = tienePermiso('TRANSFERENCIA_EDITAR')
  const [origen, setOrigen] = useState('')
  const [destino, setDestino] = useState('')
  const [observaciones, setObservaciones] = useState('')
  const [buscar, setBuscar] = useState('')
  const [lineas, setLineas] = useState<LineaTransferencia[]>([{ productoId: '', cantidad: '' }])
  const queryClient = useQueryClient()
  const page = Math.max(0, Number(params.get('page') ?? 0))
  const size = Number(params.get('size') ?? 20)
  const almacenes = useQuery({ queryKey: ['almacenes'], queryFn: inventarioApi.almacenes })
  const productos = useQuery({
    queryKey: ['productos-opciones', buscar],
    queryFn: () => catalogoApi.productos({ q: buscar || undefined, page: 0, size: 100, activo: true }),
    enabled: puedeEditar,
  })
  const filtros = {
    estado: params.get('estado') ?? undefined,
    almacenId: params.get('almacenId') ? Number(params.get('almacenId')) : undefined,
    desde: params.get('desde') ? new Date(`${params.get('desde')}T00:00:00-05:00`).toISOString() : undefined,
    hasta: params.get('hasta') ? new Date(`${params.get('hasta')}T23:59:59.999-05:00`).toISOString() : undefined,
    page, size, sort: params.get('sort') ?? 'fecha', direction: params.get('direction') ?? 'desc',
  }
  const transferencias = useQuery({
    queryKey: ['transferencias', filtros],
    queryFn: () => inventarioApi.transferencias(filtros),
  })
  const actualizar = async () => Promise.all([
    queryClient.invalidateQueries({ queryKey: ['transferencias'] }),
    queryClient.invalidateQueries({ queryKey: ['movimientos'] }),
    queryClient.invalidateQueries({ queryKey: ['stock'] }),
    queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
    queryClient.invalidateQueries({ queryKey: ['inventario'] }),
  ])
  const crear = useMutation({
    mutationFn: () => inventarioApi.crearTransferencia({
      almacenOrigenId: Number(origen), almacenDestinoId: Number(destino),
      detalles: lineas.map((linea) => ({ productoId: Number(linea.productoId), cantidad: Number(linea.cantidad) })),
      observaciones: observaciones || undefined,
    }),
    onSuccess: async (transferencia) => {
      await actualizar()
      setLineas([{ productoId: '', cantidad: '' }])
      setObservaciones('')
      toast.success(`Transferencia ${transferencia.numero} creada.`)
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.detalle : 'No se pudo crear la transferencia.'),
  })
  const accion = useMutation({
    mutationFn: ({ id, tipo }: { id: number; tipo: 'despachar' | 'recibir' | 'cancelar' }) =>
      inventarioApi[tipo](id),
    onSuccess: async (transferencia) => {
      await actualizar()
      toast.success(`${transferencia.numero}: estado ${transferencia.estado.toLowerCase()}.`)
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.detalle : 'No se pudo actualizar la transferencia.'),
  })
  const cambiar = (campo: string, valor: string) => setParams((actuales) => {
    const next = new URLSearchParams(actuales)
    if (valor) next.set(campo, valor); else next.delete(campo)
    next.delete('page')
    return next
  })
  const canCreate = Boolean(origen && destino && origen !== destino && lineas.length > 0
    && lineas.every((linea) => linea.productoId && Number(linea.cantidad) > 0))
  const almacenesDisponibles = almacenes.data
  const productosDisponibles = productos.data
  if (almacenes.isPending || transferencias.isPending || (puedeEditar && productos.isPending)) return <Skeleton />
  if (almacenes.isError || transferencias.isError || (puedeEditar && productos.isError)) {
    const error = almacenes.error ?? productos.error ?? transferencias.error
    return <ErrorState mensaje={error instanceof ApiError ? error.detalle : 'No se pudieron cargar las transferencias.'}
      reintentar={() => {
        void almacenes.refetch()
        if (puedeEditar) void productos.refetch()
        void transferencias.refetch()
      }} />
  }
  const pagina = transferencias.data
  return <section className={puedeEditar ? 'settings-grid transferencias-page' : 'transferencias-page'}>
    {puedeEditar && almacenesDisponibles && productosDisponibles && <form className="card form-card" onSubmit={(event) => { event.preventDefault(); if (canCreate) crear.mutate() }}>
      <h3>Nueva transferencia</h3>
      <label>Almacén de origen<select required value={origen} onChange={(event) => setOrigen(event.target.value)}>
        <option value="">Selecciona origen</option>{almacenesDisponibles.filter((a) => a.activo).map((a) =>
          <option value={a.id} key={a.id}>{a.nombre}</option>)}</select></label>
      <label>Almacén de destino<select required value={destino} onChange={(event) => setDestino(event.target.value)}>
        <option value="">Selecciona destino</option>{almacenesDisponibles.filter((a) => a.activo && String(a.id) !== origen).map((a) =>
          <option value={a.id} key={a.id}>{a.nombre}</option>)}</select></label>
      {origen && destino && origen === destino && <small role="alert">El origen y el destino deben ser distintos.</small>}
      <label>Buscar producto<SearchInput value={buscar} onChange={setBuscar} placeholder="Código o nombre..." /></label>
      {lineas.map((linea, index) => <div className="form-grid" key={index}>
        <label>Producto<select required value={linea.productoId} onChange={(event) => setLineas((actuales) =>
          actuales.map((item, i) => i === index ? { ...item, productoId: event.target.value } : item))}>
          <option value="">Selecciona producto</option>{productosDisponibles.contenido.map((producto) =>
            <option key={producto.id} value={producto.id}>{producto.codigo} · {producto.nombre}</option>)}</select></label>
        <label>Cantidad<input type="number" min="0.001" step="0.001" required value={linea.cantidad}
          onChange={(event) => setLineas((actuales) => actuales.map((item, i) =>
            i === index ? { ...item, cantidad: event.target.value } : item))} /></label>
        {lineas.length > 1 && <button type="button" className="btn ghost" onClick={() =>
          setLineas((actuales) => actuales.filter((_, i) => i !== index))}>Quitar línea</button>}
      </div>)}
      <button type="button" className="btn secondary" onClick={() => setLineas((actuales) =>
        [...actuales, { productoId: '', cantidad: '' }])}>Agregar producto</button>
      <label>Observaciones<textarea maxLength={2000} value={observaciones} onChange={(event) => setObservaciones(event.target.value)} /></label>
      <button className="btn primary" disabled={!canCreate || crear.isPending}>{crear.isPending ? 'Guardando…' : 'Crear pendiente'}</button>
    </form>}
    <section className="card">
      <div className="section-title"><h3>Transferencias</h3></div>
      <div className="filter-bar">
        <select aria-label="Filtrar por estado" value={params.get('estado') ?? ''} onChange={(event) => cambiar('estado', event.target.value)}>
          <option value="">Todos los estados</option>{['PENDIENTE', 'EN_TRANSITO', 'COMPLETADA', 'CANCELADA'].map((estado) =>
            <option key={estado}>{estado}</option>)}</select>
        <select aria-label="Filtrar por almacén" value={params.get('almacenId') ?? ''} onChange={(event) => cambiar('almacenId', event.target.value)}>
          <option value="">Todos los almacenes</option>{(almacenesDisponibles ?? []).map((a) => <option key={a.id} value={a.id}>{a.nombre}</option>)}
        </select>
        <label>Desde<input type="date" value={params.get('desde') ?? ''} onChange={(event) => cambiar('desde', event.target.value)} /></label>
        <label>Hasta<input type="date" value={params.get('hasta') ?? ''} onChange={(event) => cambiar('hasta', event.target.value)} /></label>
      </div>
      {pagina.contenido.length === 0 ? <p className="empty-state">No hay transferencias.</p> :
        <div className="scroll"><table><thead><tr><th>Número</th><th>Fecha</th><th>Origen</th><th>Destino</th><th>Productos</th><th>Estado</th><th>Acciones</th></tr></thead>
          <tbody>{pagina.contenido.map((transferencia) => <tr key={transferencia.id}>
            <td>{transferencia.numero}</td><td>{new Date(transferencia.fecha).toLocaleDateString('es-PE')}</td>
            <td>{transferencia.almacenOrigen}</td><td>{transferencia.almacenDestino}</td>
            <td>{transferencia.detalles.map((detalle) => `${detalle.codigo}: ${detalle.cantidad}`).join(', ')}</td>
            <td><Badge>{transferencia.estado}</Badge></td><td className="user-actions">
              {puedeEditar && transferencia.estado === 'PENDIENTE' && <>
                <button className="btn primary" disabled={accion.isPending} onClick={() => accion.mutate({ id: transferencia.id, tipo: 'despachar' })}>Despachar</button>
                <button className="btn danger" disabled={accion.isPending} onClick={() => accion.mutate({ id: transferencia.id, tipo: 'cancelar' })}>Cancelar</button></>}
              {puedeEditar && transferencia.estado === 'EN_TRANSITO' &&
                <button className="btn primary" disabled={accion.isPending} onClick={() => accion.mutate({ id: transferencia.id, tipo: 'recibir' })}>Recibir</button>}
            </td></tr>)}</tbody></table></div>}
      <Pagination page={pagina.numero} totalPages={pagina.totalPaginas} onPage={(siguiente) =>
        setParams((actuales) => { const next = new URLSearchParams(actuales); next.set('page', String(siguiente)); return next })} />
      <small className="table-total">{pagina.totalElementos} transferencias</small>
    </section>
  </section>
}
