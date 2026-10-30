import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

export function ChartCard({ data }: { data: Array<{ periodo: string; entradas: number; salidas: number; compras: number }> }) {
  return <section className="card chart-card"><div className="section-title"><div>
    <h3>Actividad del período</h3><span>Movimientos registrados</span></div></div>
    {data.length === 0 ? <p className="empty-state">No hay movimientos en este período.</p> :
      <ResponsiveContainer width="100%" height={210}><LineChart data={data}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
        <XAxis dataKey="periodo" tick={{ fill: 'var(--text)' }} stroke="var(--text-secondary)" />
        <YAxis tick={{ fill: 'var(--text)' }} stroke="var(--text-secondary)" />
        <Tooltip />
        <Line type="monotone" dataKey="entradas" stroke="var(--link)" />
        <Line type="monotone" dataKey="salidas" stroke="var(--accent)" />
        <Line type="monotone" dataKey="compras" stroke="var(--success)" />
      </LineChart></ResponsiveContainer>}
  </section>
}
