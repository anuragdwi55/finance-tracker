import React from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import api from '../api'

export default function ReportPreview() {
  const [sp] = useSearchParams()
  const navigate = useNavigate()
  const year = Number(sp.get('year')) || new Date().getFullYear()
  const month = Number(sp.get('month')) || (new Date().getMonth() + 1)

  const [html, setHtml] = React.useState<string>('')
  const [loading, setLoading] = React.useState(true)
  const [error, setError] = React.useState<string>('')

  React.useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const { data } = await api.get('/reports/preview', {
          params: { year, month },
          responseType: 'text',
        })
        if (!cancelled) setHtml(data)
      } catch (e) {
        if (!cancelled) setError('Failed to load preview (are you logged in?)')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => { cancelled = true }
  }, [year, month])

  return (
    <div className="container">
      <div className="inline-form" style={{ marginBottom: 12 }}>
        <button className="btn" onClick={() => navigate(-1)}>← Back</button>
        <div className="muted">Preview for {year}-{String(month).padStart(2, '0')}</div>
      </div>

      {loading && <div className="card">Loading…</div>}
      {!loading && error && <div className="card">{error}</div>}

      {!loading && !error && (
        <div className="card" style={{ padding: 0, overflow: 'hidden', height: '80vh' }}>
          <iframe
            title="Monthly Report"
            srcDoc={html}
            style={{ border: 0, width: '100%', height: '100%' }}
          />
        </div>
      )}
    </div>
  )
}
