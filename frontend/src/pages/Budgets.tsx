import React, { useEffect, useMemo, useState } from 'react'
import api from '../api'
import '../charts'
import { Doughnut, Bar } from 'react-chartjs-2'

type Row = { category: string; limit: number; spent: number; remaining: number; pct: number }
type Overview = { totals: { income: number; expense: number; savings: number; budgeted: number }, byCategory: Row[] }
type UpsertItem = { category: string; limit: number }

const cats = ['HOUSING','FOOD','TRANSPORT','UTILITIES','ENTERTAINMENT','HEALTH','INVESTMENT','OTHER']

export default function Budgets(){
  const [ym, setYm] = useState(()=>{
    const d = new Date(); const m = String(d.getMonth()+1).padStart(2,'0'); return `${d.getFullYear()}-${m}`
  })
  const [overview, setOverview] = useState<Overview | null>(null)

  // IMPORTANT: keep input values as strings so the field can be cleared
  const [limits, setLimits] = useState<Record<string, string>>({})

  const [saving, setSaving] = useState(false)
  const [copying, setCopying] = useState(false)

  const [y, m] = ym.split('-').map(Number)

  const load = async ()=>{
    const { data } = await api.get<Overview>(`/budgets/overview?year=${y}&month=${m}`)
    setOverview(data)

    // seed inputs; show empty string instead of 0 so user can type freely
    const next: Record<string, string> = {}
    data.byCategory.forEach(r => {
      next[r.category] = r.limit ? String(r.limit) : ''  // '' allows deletion without snapping back to 0
    })
    setLimits(next)
  }
  useEffect(()=>{ load() },[ym]) // eslint-disable-line

  const save = async ()=>{
    setSaving(true)
    try {
      // parse ONLY on save; empty -> 0
      const items: UpsertItem[] = Object.entries(limits).map(([category, raw])=>({
        category,
        limit: raw.trim() === '' ? 0 : Number(raw)
      }))
      await api.put(`/budgets?year=${y}&month=${m}`, { items })
      await load()
    } finally { setSaving(false) }
  }

  const copyLast = async ()=>{
    setCopying(true)
    try {
      const prev = new Date(y, (m - 1) - 1, 1) // JS month 0-based
      const fromYear = prev.getFullYear()
      const fromMonth = prev.getMonth() + 1
      await api.post(`/budgets/copy`, null, { params: { fromYear, fromMonth, toYear: y, toMonth: m } })
      await load()
    } finally { setCopying(false) }
  }

  // memoized chart inputs
  const labels   = useMemo(() => overview?.byCategory.map(r => r.category) ?? cats, [overview])
  const spentArr = useMemo(() => overview?.byCategory.map(r => r.spent ?? 0) ?? cats.map(()=>0), [overview])
  const limitArr = useMemo(() => overview?.byCategory.map(r => r.limit ?? 0) ?? cats.map(()=>0), [overview])
  const colors = ['#2563eb','#16a34a','#dc2626','#d97706','#7c3aed','#0891b2','#9333ea','#f43f5e']
  const doughnutData = useMemo(() => ({ labels, datasets: [{ label:'Spent', data: spentArr, backgroundColor: colors.slice(0,spentArr.length), borderWidth: 1 }] }), [labels, spentArr])
  const barData = useMemo(() => ({
    labels,
    datasets: [
      { label:'Limit', data: limitArr, backgroundColor: 'rgba(0,0,0,0.2)' },
      { label:'Spent', data: spentArr, backgroundColor: colors.slice(0, spentArr.length) }
    ]
  }), [labels, limitArr, spentArr])

  const chartOptions = useMemo(() => ({
    responsive: true,
    maintainAspectRatio: false,
    animation: false as const,
    resizeDelay: 300,
  }), [])

  return (
    <div style={{ padding:24, maxWidth:1000, margin:'0 auto' }}>
      <h2 style={{ fontSize:22, marginBottom:12 }}>Budgets</h2>

      <div style={{ display:'flex', gap:12, alignItems:'center', marginBottom:16 }}>
        <label>Month: <input type="month" value={ym} onChange={e=>setYm(e.target.value)} /></label>
        <button onClick={save} disabled={saving}
          style={{ padding:'8px 12px', borderRadius:8, border:'1px solid #000', background:'#000', color:'#fff' }}>
          {saving ? 'Saving…' : 'Save Budgets'}
        </button>
        <button onClick={copying ? undefined : copyLast} disabled={copying}
          style={{ padding:'8px 12px', borderRadius:8, border:'1px solid #ddd', background:'#fff' }}>
          {copying ? 'Copying…' : 'Copy last month'}
        </button>
      </div>

      {overview && (
        <>
          <div style={{ display:'grid', gridTemplateColumns:'repeat(4,1fr)', gap:12, marginBottom:16 }}>
            <Stat label="Income"   value={overview.totals.income} />
            <Stat label="Expenses" value={overview.totals.expense} />
            <Stat label="Savings"  value={overview.totals.savings} />
            <Stat label="Budgeted" value={overview.totals.budgeted} />
          </div>

          <div style={{ overflow:'auto', borderRadius:12, boxShadow:'0 1px 6px rgba(0,0,0,.08)', background:'#fff' }}>
            <table style={{ width:'100%', fontSize:14, borderCollapse:'collapse' }}>
              <thead style={{ background:'#f5f5f5' }}>
                <tr><th style={{textAlign:'left',padding:12}}>Category</th>
                    <th style={{textAlign:'right',padding:12}}>Limit</th>
                    <th style={{textAlign:'right',padding:12}}>Spent</th>
                    <th style={{textAlign:'right',padding:12}}>Remaining</th>
                    <th style={{padding:12}}>Progress</th></tr>
              </thead>
              <tbody>
                {cats.map(c=>{
                  const row = overview.byCategory.find(r=>r.category===c) || {category:c,limit:0,spent:0,remaining:0,pct:0}
                  const over = row.remaining < 0
                  return (
                    <tr key={c} style={{ borderTop:'1px solid #eee' }}>
                      <td style={{ padding:12 }}>{c}</td>
                      <td style={{ padding:12, textAlign:'right' }}>
                        <input
                          type="number"
                          step="0.01"
                          value={limits[c] ?? ''}        // keep as string
                          placeholder="0"
                          onChange={(e)=> setLimits(prev => ({ ...prev, [c]: e.target.value }))}
                          style={{ width:120 }}
                        />
                      </td>
                      <td style={{ padding:12, textAlign:'right' }}>₹ {fmt(row.spent)}</td>
                      <td style={{ padding:12, textAlign:'right', color: over ? '#b91c1c' : undefined }}>
                        {over ? <>Over by ₹ {fmt(-row.remaining)}</> : <>₹ {fmt(row.remaining)}</>}
                      </td>
                      <td style={{ padding:12 }}>
                        <BarPct pct={row.pct} over={over}/>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          {/* Charts */}
          <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:16, marginTop:16 }}>
            <div style={{ padding:16, borderRadius:12, boxShadow:'0 1px 6px rgba(0,0,0,.08)', background:'#fff' }}>
              <div style={{fontSize:14, fontWeight:600, marginBottom:8}}>Spend by Category</div>
              <div style={{ position:'relative', height:320 }}>
                <Doughnut key={`d-${y}-${m}`} data={doughnutData} options={chartOptions} datasetIdKey="label" />
              </div>
            </div>
            <div style={{ padding:16, borderRadius:12, boxShadow:'0 1px 6px rgba(0,0,0,.08)', background:'#fff' }}>
              <div style={{fontSize:14, fontWeight:600, marginBottom:8}}>Spent vs Limit</div>
              <div style={{ position:'relative', height:320 }}>
                <Bar key={`b-${y}-${m}`} data={barData} options={chartOptions} datasetIdKey="label" />
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  )
}

function fmt(n:number){ return new Intl.NumberFormat().format(Math.round((n??0)*100)/100) }

function Stat({label,value}:{label:string;value:number}){
  return <div style={{ padding:16, borderRadius:12, boxShadow:'0 1px 6px rgba(0,0,0,.08)', background:'#fff' }}>
    <div style={{fontSize:12,opacity:.7}}>{label}</div>
    <div style={{fontSize:20,fontWeight:600}}>₹ {fmt(value)}</div>
  </div>
}

function BarPct({pct, over}:{pct:number; over?:boolean}){
  const w = Math.max(0, Math.min(1, pct||0))
  return (
    <div style={{ background:'#eee', height:10, borderRadius:6, width:200 }}>
      <div style={{
        width:`${(w*100).toFixed(0)}%`,
        height:10,
        borderRadius:6,
        background: over ? '#dc2626' : '#000'
      }} />
    </div>
  )
}
