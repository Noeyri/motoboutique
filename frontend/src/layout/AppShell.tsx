import { useEffect, useMemo, useState } from 'react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { useEstadoPersistente } from '../hooks/useEstadoPersistente'
import { rutasPermitidas } from '../app/routes'
import { useAuth } from '../auth/AuthContext'
import { Icon } from '../components/Icon'
import { Logo } from '../components/Logo'

export interface AppShellContext {
  pageSize: number
  setPageSize: (size: number) => void
}

export function AppShell() {
  const { usuario, logout, tienePermiso } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [sidebar, setSidebar] = useState(false)
  const [preferencias, setPreferencias, preferenciasCargadas] =
    useEstadoPersistente('preferencias', { tema: 'light', tamanioPagina: 20, filtros: {} as Record<string, string> })
  const permitidas = useMemo(() => rutasPermitidas(tienePermiso), [tienePermiso])
  const nav = useMemo(() => permitidas.filter((item) => item.enMenu !== false), [permitidas])
  const actual = permitidas.find((item) => location.pathname === item.path) ?? nav[0]
  const rolTexto = usuario?.roles.join(', ') ?? 'Usuario'
  const iconos: Record<string, string> = {
    'Dashboard general': 'grid', 'Dashboard de almacén': 'grid', 'Dashboard de finanzas': 'grid',
    'Dashboard de consulta': 'grid', Usuarios: 'users', 'Roles y permisos': 'shield', Configuración: 'settings',
    Aprobaciones: 'check', Movimientos: 'move', Entradas: 'plus', Salidas: 'logout',
    Transferencias: 'arrow', 'Conteo físico': 'check', Ajustes: 'settings', Productos: 'box',
    Motocicletas: 'bike', Importación: 'upload', Proveedores: 'users', Órdenes: 'cart',
    Recepciones: 'file', Valorización: 'chart', Kardex: 'file', Reportes: 'chart',
    Stock: 'box', Compatibilidades: 'bike', Catálogo: 'file',
  }

  useEffect(() => {
    if (!preferenciasCargadas) return
    const actual = preferencias.filtros?.[location.pathname]
    if (actual !== location.search) {
      setPreferencias((previas) => ({
        ...previas,
        filtros: { ...(previas.filtros ?? {}), [location.pathname]: location.search },
      }))
    }
  }, [location.pathname, location.search, preferencias.filtros, preferenciasCargadas, setPreferencias])

  useEffect(() => {
    if (preferenciasCargadas) document.documentElement.classList.toggle('dark', preferencias.tema === 'dark')
  }, [preferencias, preferenciasCargadas])

  const cerrar = async () => {
    try { await logout(); navigate('/login', { replace: true }) }
    catch { toast.error('No se pudo cerrar la sesión.'); navigate('/login', { replace: true }) }
  }
  const alternarTema = () => setPreferencias((actuales) => ({
    ...actuales, tema: actuales.tema === 'dark' ? 'light' : 'dark',
  }))

  return <div className={`app ${preferencias.tema === 'dark' ? 'dark' : ''}`}>
    <aside className={sidebar ? 'sidebar open' : 'sidebar'}>
      <div className="brand"><Logo /><b>Moto Boutique</b></div>
      <div className="role-label">ESPACIO DE {rolTexto.toUpperCase()}</div>
      <nav>{nav.map((item, index) => <div className="nav-entry" key={item.path}>
        {item.grupo && item.grupo !== nav[index - 1]?.grupo && <span className="nav-group">{item.grupo}</span>}
        <Link className={location.pathname === item.path ? 'active' : ''}
          to={`${item.path}${preferencias.filtros?.[item.path] ?? ''}`} onClick={() => setSidebar(false)}>
          <Icon name={iconos[item.label] ?? 'grid'} /><span>{item.label}</span></Link>
      </div>)}</nav>
      <div className="sidebar-user"><span className="avatar">{(usuario?.nombre ?? 'U').slice(0, 2).toUpperCase()}</span>
        <p><b>{usuario?.nombre ?? 'Usuario'}</b><small>{rolTexto}</small></p>
        <button onClick={() => void cerrar()} aria-label="Cerrar sesión"><Icon name="logout" /></button></div>
    </aside>
    {sidebar && <button className="scrim" onClick={() => setSidebar(false)} aria-label="Cerrar menú" />}
    <div className="workspace"><header>
      <button className="mobile-menu" onClick={() => setSidebar(true)} aria-label="Abrir menú"><Icon name="menu" /></button>
      <div><span>MOTO BOUTIQUE / {rolTexto.toUpperCase()}</span><h1>{actual?.label ?? 'Panel'}</h1></div>
      <div className="top-actions"><div className="theme-toggle"><Icon name="sun" size={16} />
        <button onClick={alternarTema} aria-label="Cambiar tema"><i className={preferencias.tema === 'dark' ? 'on' : ''} /></button><Icon name="moon" size={16} />
      </div></div>
    </header><main><Outlet context={{
      pageSize: preferencias.tamanioPagina,
      setPageSize: (size: number) => setPreferencias((previas) => ({ ...previas, tamanioPagina: size })),
    } satisfies AppShellContext} /></main></div>
  </div>
}
