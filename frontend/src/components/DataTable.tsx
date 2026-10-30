import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { useOutletContext, useSearchParams } from 'react-router-dom'
import type { AppShellContext } from '../layout/AppShell'
import { Pagination } from './Pagination'
import { SearchInput } from './SearchInput'
import { FilterBar } from './FilterBar'
import { EmptyState } from './EmptyState'
import { Skeleton } from './Skeleton'
import { ErrorState } from './ErrorState'

export interface DataTableColumn<T> { key: string; label: string; render?: (row: T) => ReactNode }

export function DataTable<T extends { id: string | number }>({
  rows, columns, total, totalPages, loading, error, onRetry, onExport,
  extraFilters, toolbarActions,
}: {
  rows: T[]; columns: DataTableColumn<T>[]; total: number; totalPages: number
  loading: boolean; error?: string; onRetry?: () => void; onExport?: () => void
  extraFilters?: ReactNode; toolbarActions?: ReactNode
}) {
  const [params, setParams] = useSearchParams()
  const { pageSize, setPageSize } = useOutletContext<AppShellContext>()
  const [seleccion, setSeleccion] = useState<Set<string | number>>(new Set())
  const q = params.get('q') ?? ''
  const page = Math.max(0, Number(params.get('page') ?? 0))
  const size = Math.max(1, Number(params.get('size') ?? pageSize))
  const setParam = useCallback((name: string, value: string) => {
    setParams((previous) => {
      const next = new URLSearchParams(previous)
      if (value) next.set(name, value); else next.delete(name)
      return next
    })
  }, [setParams])
  const toggle = (id: string | number) => setSeleccion((prev) => {
    const next = new Set(prev); if (next.has(id)) next.delete(id); else next.add(id); return next
  })
  const allSelected = rows.length > 0 && rows.every((row) => seleccion.has(row.id))
  const toggleAll = () => setSeleccion(allSelected ? new Set() : new Set(rows.map((row) => row.id)))
  const columnHeaders = useMemo(() => columns, [columns])

  if (loading) return <Skeleton />
  if (error) return <ErrorState mensaje={error} reintentar={onRetry} />
  return <div className="table-wrap">
    <FilterBar><SearchInput value={q} onChange={(v) => setParams((previous) => {
      const next = new URLSearchParams(previous)
      if (v) next.set('q', v); else next.delete('q')
      next.delete('page')
      return next
    })} />
      <select aria-label="Tamaño de página" value={size} onChange={(e) => {
        const nuevoTamanio = Number(e.target.value)
        setPageSize(nuevoTamanio)
        setParams((previous) => {
          const next = new URLSearchParams(previous)
          next.set('size', e.target.value)
          next.delete('page')
          return next
        })
      }}>
        {[10, 20, 50, 100].map((n) => <option key={n}>{n}</option>)}
      </select>{extraFilters}{onExport && <button className="btn secondary" onClick={onExport}>Exportar CSV</button>}
      {toolbarActions}
    </FilterBar>
    {rows.length === 0 ? <EmptyState title="Sin resultados" description="No hay registros para los filtros seleccionados." /> :
      <div className="scroll"><table><thead><tr><th><input type="checkbox" checked={allSelected} onChange={toggleAll} aria-label="Seleccionar todos" /></th>
        {columnHeaders.map((column) => <th key={column.key}><button className="table-sort"
          onClick={() => setParams((previous) => {
            const next = new URLSearchParams(previous)
            const sortKey = column.key === 'id' ? 'nombre' : column.key
            const direction = next.get('sort') === sortKey && next.get('direction') === 'asc' ? 'desc' : 'asc'
            next.set('sort', sortKey)
            next.set('direction', direction)
            next.delete('page')
            return next
          })}>
          {column.label}</button></th>)}</tr></thead><tbody>
        {rows.map((row) => <tr key={row.id}><td><input type="checkbox" checked={seleccion.has(row.id)}
          onChange={() => toggle(row.id)} aria-label={`Seleccionar fila ${row.id}`} /></td>
          {columnHeaders.map((column) => <td key={column.key}>{column.render ? column.render(row) : String(row[column.key as keyof T] ?? '')}</td>)}
        </tr>)}</tbody></table></div>}
    <div className="table-footer">
      <small className="table-total">{total} {total === 1 ? 'registro' : 'registros'}</small>
      <Pagination page={page} totalPages={totalPages} onPage={(next) => setParam('page', String(next))} />
    </div>
  </div>
}
