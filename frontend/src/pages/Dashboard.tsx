// frontend/src/pages/Dashboard.tsx
import React, { useEffect, useMemo, useRef, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../api'
import '../charts'
import { Line } from 'react-chartjs-2'

type Trend = { labels: string[]; income: number[]; expense: number[]; savings: number[] }

export default function Dashboard() {
  const [forecast, setForecast] = useState<number | null>(null)
  const [loading, setLoading] = useState(false)
  const [ym, setYm] = useState(() => {
    const d = new Date()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    return `${d.getFullYear()}-${m}`
  })
  const [msg, setMsg] = useState('')
  const [trend, setTrend] = useState<Trend | null>(null)
  const navigate = useNavigate()

  // guard React 18 StrictMode double-run
  const did = useRef(false)
  useEffect(() => {
    if (did.current) return
    did.current = true
    ;(async () => {
      setLoading(true)
      try {
        const [{ data: f }, { data: t }] = await Promise.all([
          api.get('/insights/forecast'),
          api.get('/insights/trend', { params: { months: 6 } }),
        ])
        setForecast(f?.next_month_savings ?? null)
        setTrend(t)
      } finally {
        setLoading(false)
      }
    })()
  }, [])

  const trendData = useMemo(
    () =>
      trend
        ? {
            labels: trend.labels,
            datasets: [
              { label: 'Income', data: trend.income, backgroundColor: '#f00' },
              { label: 'Expenses', data: trend.expense, backgroundColor: '#0f0' },
              { label: 'Savings', data: trend.savings, backgroundColor: '#00f' },
            ],
          }
        : null,
    [trend]
  )

  const trendOptions = useMemo(
    () => ({
      responsive: true,
      maintainAspectRatio: false,
      animation: false as const,
      resizeDelay: 300,
    }),
    []
  )

  const [year, month] = ym.split('-').map(Number)

  const preview = useCallback(() => {
    navigate(`/report-preview?year=${year}&month=${month}`)
  }, [navigate, year, month])

  const send = useCallback(async () => {
    setMsg('')
    await api.post('/reports/send', null, { params: { year, month } })
    setMsg('Report email sent! Check Mailhog (http://localhost:8025).')
  }, [year, month])

  const card: React.CSSProperties = {
    padding: 16,
    borderRadius: 12,
    boxShadow: '0 1px 6px rgba(0,0,0,.08)',
    background: '#fff',
  }

  return (
    <div style={{ padding: 24, maxWidth: 960, margin: '0 auto' }}>
      <h2 style={{ fontSize: 22, marginBottom: 12 }}>Dashboard</h2>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 16, marginBottom: 16 }}>
        <div style={card}>
          <div style={{ fontSize: 12, opacity: 0.7 }}>Next Month Forecast (Savings)</div>
          <div style={{ fontSize: 20, fontWeight: 600 }}>
            {loading ? 'Loading…' : forecast !== null ? `₹ ${new Intl.NumberFormat().format(Math.round(forecast))}` : '—'}
          </div>
        </div>

        <div style={card}>
          <div style={{ fontSize: 12, opacity: 0.7 }}>Monthly Report</div>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 6 }}>
            <input type="month" value={ym} onChange={(e) => setYm(e.target.value)} />
            <button onClick={preview}>Preview</button>
            <button onClick={send}>Send</button>
          </div>
          {msg && <div style={{ marginTop: 8, fontSize: 12, color: '#0a0' }}>{msg}</div>}
        </div>
      </div>

      {trendData && (
        <div style={{ padding: 16, borderRadius: 12, boxShadow: '0 1px 6px rgba(0,0,0,.08)', background: '#fff', marginBottom: 16 }}>
          <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 8 }}>
            Savings Trend (Last {trendData.labels.length} months)
          </div>
          <div style={{ position: 'relative', height: 260 }}>
            <Line data={trendData} options={trendOptions} datasetIdKey="label" />
          </div>
        </div>
      )}
    </div>
  )
}
