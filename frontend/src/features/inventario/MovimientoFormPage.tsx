import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { catalogoApi } from '../../api/catalogo'
import { ApiError } from '../../api/client'
import { Badge } from '../../components/Badge'
import { ErrorState } from '../../components/ErrorState'
import { Skeleton } from '../../components/Skeleton'
import { inventarioApi } from './api'
import type { Producto } from '../../api/types'

export function MovimientoFormPage({ tipo }: { tipo: 'entrada' | 'salida' }) {
  const queryClient = useQueryClient()
  const [buscar, setBuscar] = useState('')
  const [textoProducto, setTextoProducto] = useState('')
  const [productoId, setProductoId] = useState('')
  const [productoSeleccionado, setProductoSeleccionado] = useState<Producto | null>(null)
  const [opcionesAbiertas, setOpcionesAbiertas] = useState(false)
  const [opcionActiva, setOpcionActiva] = useState(0)
  const [almacenId, setAlmacenId] = useState('')
  const [cantidad, setCantidad] = useState('')
  const [costo, setCosto] = useState('')
  const [motivo, setMotivo] = useState('VENTA_MOSTRADOR')
  const [referencia, setReferencia] = useState('')
  const [observaciones, setObservaciones] = useState('')
  useEffect(() => {
    const timer = window.setTimeout(() => {
      if (!productoId) setBuscar(textoProducto)
    }, 250)
    return () => window.clearTimeout(timer)
  }, [textoProducto, productoId])
  const almacenes = useQuery({ queryKey: ['almacenes'], queryFn: inventarioApi.almacenes })
  const productos = useQuery({
    queryKey: ['productos-opciones', buscar],
    queryFn: () => catalogoApi.productos({ q: buscar || undefined, page: 0, size: 100, activo: true }),
  })
  const selectedProduct = productoSeleccionado
  const stock = useQuery({
    queryKey: ['stock-producto', selectedProduct?.codigo, almacenId],
    queryFn: () => inventarioApi.stock({ q: selectedProduct?.codigo, almacenId: Number(almacenId), page: 0, size: 100 }),
    enabled: tipo === 'salida' && Boolean(selectedProduct && almacenId),
  })
  const recientes = useQuery({
    queryKey: ['movimientos-recientes', tipo],
    queryFn: () => inventarioApi.movimientos({
      tipo: tipo === 'entrada' ? 'ENTRADA' : 'SALIDA', page: 0, size: 10, sort: 'fecha', direction: 'desc',
    }),
  })
  const guardar = useMutation({
    mutationFn: () => tipo === 'entrada'
      ? inventarioApi.entrada({
        productoId: Number(productoId), almacenId: Number(almacenId), cantidad: Number(cantidad),
        costoUnitario: Number(costo), documentoReferencia: referencia || undefined, observaciones: observaciones || undefined,
      })
      : inventarioApi.salida({
        productoId: Number(productoId), almacenId: Number(almacenId), cantidad: Number(cantidad),
        motivo, documentoReferencia: referencia || undefined, observaciones: observaciones || undefined,
      }),
    onSuccess: async (movimiento) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['movimientos-recientes', tipo] }),
        queryClient.invalidateQueries({ queryKey: ['movimientos'] }),
        queryClient.invalidateQueries({ queryKey: ['stock'] }),
        queryClient.invalidateQueries({ queryKey: ['stock-producto'] }),
        queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
        queryClient.invalidateQueries({ queryKey: ['inventario'] }),
      ])
      setCantidad('')
      setCosto('')
      setReferencia('')
      setObservaciones('')
      setProductoId('')
      setProductoSeleccionado(null)
      setTextoProducto('')
      toast.success(`${tipo === 'entrada' ? 'Entrada' : 'Salida'} registrada: ${movimiento.numero}.`)
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.detalle : 'No se pudo registrar el movimiento.'),
  })
  const stockDisponible = stock.data?.contenido.find((fila) => fila.almacenId === Number(almacenId)
    && fila.codigo === selectedProduct?.codigo)
  const amountValid = Number(cantidad) > 0
  const canSubmit = Boolean(productoId && almacenId && amountValid
    && (tipo === 'entrada' ? Number(costo) >= 0 : stockDisponible && Number(cantidad) <= stockDisponible.cantidad - stockDisponible.reservado))
  if (almacenes.isPending || productos.isPending || recientes.isPending) return <Skeleton />
  if (almacenes.isError || productos.isError || recientes.isError) {
    const error = almacenes.error ?? productos.error ?? recientes.error
    return <ErrorState mensaje={error instanceof ApiError ? error.detalle : 'No se pudieron cargar los datos del movimiento.'}
      reintentar={() => { void almacenes.refetch(); void productos.refetch(); void recientes.refetch() }} />
  }
  return <section className="settings-grid">
    <form className="card form-card" onSubmit={(event) => { event.preventDefault(); if (canSubmit) guardar.mutate() }}>
      <h3>{tipo === 'entrada' ? 'Registrar entrada' : 'Registrar salida'}</h3>
      <label className="autocomplete-label" htmlFor="movimiento-producto">Buscar producto
        <div className="product-autocomplete">
        <input id="movimiento-producto" required role="combobox" aria-autocomplete="list" aria-required="true"
          aria-expanded={opcionesAbiertas && !productoId}
          aria-controls="movimiento-producto-opciones"
          aria-activedescendant={opcionesAbiertas && !productoId && productos.data.contenido[opcionActiva]
            ? `movimiento-producto-${productos.data.contenido[opcionActiva].id}` : undefined}
          autoComplete="off" placeholder="Escribe el código o nombre del producto..."
          value={textoProducto}
          onFocus={() => { if (!productoId && textoProducto.trim()) setOpcionesAbiertas(true) }}
          onChange={(event) => {
            setTextoProducto(event.target.value)
            setProductoId('')
            setProductoSeleccionado(null)
            setOpcionActiva(0)
            setOpcionesAbiertas(true)
          }}
          onKeyDown={(event) => {
            if (event.key === 'ArrowDown' && !productoId) {
              event.preventDefault()
              setOpcionesAbiertas(true)
              if (productos.data.contenido.length) {
                setOpcionActiva((actual) => Math.min(actual + 1, productos.data.contenido.length - 1))
              }
            } else if (event.key === 'ArrowUp' && !productoId) {
              event.preventDefault()
              setOpcionActiva((actual) => Math.max(actual - 1, 0))
            } else if (event.key === 'Enter' && opcionesAbiertas && !productoId && productos.data.contenido[opcionActiva]) {
              event.preventDefault()
              const producto = productos.data.contenido[opcionActiva]
              setProductoId(String(producto.id))
              setProductoSeleccionado(producto)
              setTextoProducto(`${producto.codigo} · ${producto.nombre}`)
              setOpcionesAbiertas(false)
            } else if (event.key === 'Escape') {
              setOpcionesAbiertas(false)
            }
          }}
          onBlur={() => window.setTimeout(() => setOpcionesAbiertas(false), 120)} />
        {opcionesAbiertas && !productoId && textoProducto.trim() && <ul id="movimiento-producto-opciones"
          className="product-autocomplete-options" role="listbox" aria-label="Productos coincidentes">
          {buscar !== textoProducto || productos.isFetching
            ? <li className="product-autocomplete-empty" role="status">Buscando productos…</li>
            : productos.data.contenido.length === 0
              ? <li className="product-autocomplete-empty">No se encontraron productos.</li>
              : productos.data.contenido.map((producto, index) => <li key={producto.id} role="presentation">
                <button type="button" role="option" id={`movimiento-producto-${producto.id}`}
                  aria-selected={index === opcionActiva} onMouseDown={(event) => event.preventDefault()}
                  onMouseEnter={() => setOpcionActiva(index)}
                  onClick={() => {
                    setProductoId(String(producto.id))
                    setProductoSeleccionado(producto)
                    setTextoProducto(`${producto.codigo} · ${producto.nombre}`)
                    setOpcionesAbiertas(false)
                  }}>
                  <strong>{producto.codigo}</strong><span>{producto.nombre}</span>
                  <small>Stock: {producto.stockTotal.toLocaleString('es-PE')}</small>
                </button>
              </li>)}
        </ul>}
        </div>
      </label>
      <label>Almacén<select required value={almacenId} onChange={(event) => setAlmacenId(event.target.value)}>
        <option value="">Selecciona un almacén</option>{almacenes.data.filter((a) => a.activo).map((almacen) =>
          <option value={almacen.id} key={almacen.id}>{almacen.nombre}</option>)}
      </select></label>
      {tipo === 'salida' && <p className="field-hint">Disponible: {stockDisponible
        ? (stockDisponible.cantidad - stockDisponible.reservado).toLocaleString('es-PE', { minimumFractionDigits: 3 })
        : stock.isPending ? 'consultando…' : '0.000'}</p>}
      {selectedProduct && <p className="field-hint">Stock total actual: {selectedProduct.stockTotal.toLocaleString('es-PE')}</p>}
      <label>Cantidad<input required type="number" min="0.001" step="0.001" value={cantidad}
        onChange={(event) => setCantidad(event.target.value)} /></label>
      {tipo === 'entrada'
        ? <label>Costo unitario (S/)<input required type="number" min="0" step="0.01" value={costo}
          onChange={(event) => setCosto(event.target.value)} /></label>
        : <label>Motivo<select value={motivo} onChange={(event) => setMotivo(event.target.value)}>
          <option value="VENTA_MOSTRADOR">Venta mostrador</option><option value="ORDEN_TALLER">Orden de taller</option>
          <option value="MERMA">Merma</option><option value="OTRO">Otro</option></select></label>}
      <label>Documento de referencia<input maxLength={80} value={referencia} onChange={(event) => setReferencia(event.target.value)} /></label>
      <label>Observaciones<textarea maxLength={255} value={observaciones} onChange={(event) => setObservaciones(event.target.value)} /></label>
      {tipo === 'salida' && amountValid && stockDisponible && Number(cantidad) > stockDisponible.cantidad - stockDisponible.reservado &&
        <small role="alert">La cantidad supera el stock disponible.</small>}
      <button className="btn primary" disabled={!canSubmit || guardar.isPending}>
        {guardar.isPending ? 'Registrando…' : `Registrar ${tipo}`}</button>
    </form>
    <section className="card"><h3>Movimientos recientes</h3>
      {recientes.data.contenido.length === 0 ? <p className="empty-state">Sin movimientos recientes.</p> :
        <div className="scroll"><table><thead><tr><th>Documento</th><th>Fecha</th><th>Producto</th><th>Almacén</th><th>Cantidad</th><th>Estado</th></tr></thead>
          <tbody>{recientes.data.contenido.map((item) => <tr key={item.id}><td>{item.numero}</td>
            <td>{new Date(item.fecha).toLocaleString('es-PE')}</td><td>{item.productoCodigo} · {item.productoNombre}</td>
            <td>{item.almacen}</td><td>{item.cantidad}</td><td><Badge>{item.tipo}</Badge></td></tr>)}</tbody></table></div>}
    </section>
  </section>
}
