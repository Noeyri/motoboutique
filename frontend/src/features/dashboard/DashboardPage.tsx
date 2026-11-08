import { useState } from 'react'
import { useLocation } from 'react-router-dom'
import { useDashboard } from './hooks'
import { ChartCard } from '../../components/ChartCard'
import { Metric } from '../../components/Metric'
import { Badge } from '../../components/Badge'
import { ErrorState } from '../../components/ErrorState'
import { Skeleton } from '../../components/Skeleton'
import { EmptyState } from '../../components/EmptyState'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { inventarioApi } from '../inventario/api'

export function DashboardPage() {
  const { pathname } = useLocation()
  const rol = pathname.startsWith('/admin') ? 'admin'
    : pathname.startsWith('/almacen') ? 'almacen'
      : pathname.startsWith('/finanzas') ? 'finanzas' : 'consulta'
  const [dias, setDias] = useState(7)
  const query = useDashboard(rol, dias)
  const transferencias = useQuery({
    queryKey: ['dashboard-transferencias-transito'],
    queryFn: () => inventarioApi.transferencias({ estado: 'EN_TRANSITO', page: 0, size: 10 }),
    enabled: rol === 'almacen',
  })
  if (query.isPending) return <Skeleton />
  if (query.isError) return <ErrorState mensaje="No se pudo cargar el dashboard." reintentar={() => void query.refetch()} />
  const data = query.data
  const kpis = data.kpis
  const items = Object.entries(kpis)
  return <section className="dashboard-page">
    <div className="metrics">{items.map(([clave, valor]) =>
      <Metric key={clave} label={etiqueta(clave)} value={typeof valor === 'number'
        ? valor.toLocaleString('es-PE', { maximumFractionDigits: 2 }) : String(valor)} icon={<span>↗</span>} />)}</div>
    {(rol === 'admin' || rol === 'almacen') && <label className="section-space">Período
      <select value={dias} onChange={(event) => setDias(Number(event.target.value))}>
        {[7, 30, 90].map((rango) => <option key={rango} value={rango}>{rango} días</option>)}
      </select>
    </label>}
    {data.serie.length > 0 && <div className="dashboard-grid"><ChartCard data={data.serie} />
      {rol === 'admin' && <section className="card activity"><h3>Actividad reciente</h3>
        {data.actividad.length === 0 ? <EmptyState title="Sin actividad" description="No hay eventos de auditoría recientes." />
          : data.actividad.map((item, index) => <div key={`${item.fecha}-${index}`}><span className="avatar">{item.actor.slice(0, 1)}</span>
            <p><b>{item.accion} · {item.entidad}</b><small>{new Date(item.fecha).toLocaleString('es-PE')}</small></p></div>)}
      </section>}
    </div>}
    {(rol === 'admin' || data.criticos.length > 0) && <section className="card section-space"><div className="section-title"><h3>Productos críticos</h3></div>
      {data.criticos.length === 0 ? <EmptyState title="Sin alertas de stock" description="No hay productos por debajo del stock mínimo." /> :
        <div className="scroll"><table><thead><tr><th>Código</th><th>Producto</th><th>Categoría</th><th>Stock</th><th>Mínimo</th><th>Estado</th></tr></thead>
          <tbody>{data.criticos.map((item) => <tr key={item.productoId}><td>{item.codigo}</td><td>
            <Link to={`/catalogo/productos?q=${encodeURIComponent(item.codigo)}`}>{item.producto}</Link></td>
            <td>{item.categoria}</td><td>{item.cantidad}</td><td>{item.minimo}</td><td><Badge>{item.estado}</Badge></td></tr>)}</tbody>
        </table></div>}</section>}
    {rol === 'almacen' && <section className="card section-space">
      <div className="section-title"><div><h3>Transferencias en tránsito</h3>
        <span>Transferencias pendientes de recepción en el almacén destino.</span></div>
        <Link className="btn secondary" to="/almacen/transferencias?estado=EN_TRANSITO">Ver todas</Link></div>
      {transferencias.isPending ? <Skeleton /> : transferencias.isError
        ? <ErrorState mensaje="No se pudieron cargar las transferencias en tránsito."
          reintentar={() => void transferencias.refetch()} />
        : transferencias.data.contenido.length === 0 ? <EmptyState title="Sin transferencias en tránsito"
          description="No hay transferencias pendientes de recepción." />
          : <div className="scroll"><table><thead><tr><th>Número</th><th>Fecha</th><th>Origen</th><th>Destino</th></tr></thead>
            <tbody>{transferencias.data.contenido.map((transferencia) => <tr key={transferencia.id}>
              <td><Link to="/almacen/transferencias?estado=EN_TRANSITO">{transferencia.numero}</Link></td>
              <td>{new Date(transferencia.fecha).toLocaleDateString('es-PE')}</td>
              <td>{transferencia.almacenOrigen}</td><td>{transferencia.almacenDestino}</td>
            </tr>)}</tbody></table></div>}
    </section>}
    {rol === 'admin' && <section className="card section-space">
      {Number(kpis.ajustesPendientes ?? 0) > 0
        ? <div className="section-title"><div><h3>Aprobaciones pendientes</h3>
          <span>{Number(kpis.ajustesPendientes).toLocaleString('es-PE')} ajustes requieren revisión.</span></div>
          <Link className="btn primary" to="/admin/aprobaciones">Revisar bandeja</Link></div>
        : <EmptyState title="Sin aprobaciones pendientes" description="No hay ajustes que requieran revisión." />}
    </section>}
  </section>
}

function etiqueta(clave: string) {
  const labels: Record<string, string> = {
    usuariosActivos: 'Usuarios activos', productosActivos: 'Productos activos',
    stockValorizado: 'Stock valorizado', alertasStock: 'Alertas de stock',
    ajustesPendientes: 'Ajustes pendientes', entradasHoy: 'Entradas de hoy',
    salidasHoy: 'Salidas de hoy', transferenciasEnTransito: 'Transferencias en tránsito',
    productosBajoMinimo: 'Productos bajo mínimo', valorInventario: 'Valor del inventario',
    comprasDelMes: 'Compras del mes', rotacion12Meses: 'Rotación 12 meses',
    productosDisponibles: 'Productos disponibles', productosAgotados: 'Productos agotados',
  }
  return labels[clave] ?? clave
}
