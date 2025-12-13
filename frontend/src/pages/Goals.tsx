import React, { useEffect, useState } from 'react'
import api from '../api'

type GoalView = {
  id: number
  name: string
  targetAmount: number
  targetDate: string
  status: 'ACTIVE'|'PAUSED'|'COMPLETED'
  contributed: number
  remaining: number
  progressPct: number
  monthlyNeeded: number
  monthsLeft: number
}

type PlanItem = { goalId: number; name: string; allocated: number }
type Plan = { available: number; totalNeed: number; items: PlanItem[] }

export default function Goals(){
  const [list, setList] = useState<GoalView[]>([])
  const [name, setName] = useState('')
  const [target, setTarget] = useState<string>('')         // keep as string to avoid 0 flicker
  const [date, setDate] = useState<string>('')
  const [plan, setPlan] = useState<Plan | null>(null)
  const [cap, setCap] = useState<string>('')               // optional monthly cap override
  const [loading, setLoading] = useState(false)
  const [affectsBudget, setAffectsBudget] = React.useState(true)

  const load = async ()=>{
    setLoading(true)
    try {
      const { data } = await api.get<GoalView[]>('/goals')
      setList(data)
      const planUrl = cap ? `/goals/plan?monthly=${encodeURIComponent(cap)}` : '/goals/plan'
      const p = await api.get<Plan>(planUrl)
      setPlan(p.data)
    } catch (e) {
      console.error(e)
      alert('Failed to load goals/plan')
    } finally {
      setLoading(false)
    }
  }

  useEffect(()=>{ load() }, []) // initial

  const create = async ()=>{
    const t = Number.parseFloat(target)
    if (!name || !t || !date) return alert('Fill name, target and date')
    try {
      await api.post('/goals', { name, targetAmount: t, targetDate: date })
      setName(''); setTarget(''); setDate('')
      await load()
    } catch (e) {
      console.error(e)
      alert('Failed to create goal')
    }
  }

  const contribute = async (id:number)=>{
    const amt = Number.parseFloat(String(prompt('Amount to contribute now?') ?? ''))
    if (!Number.isFinite(amt) || amt <= 0) return
    const note = String(prompt('Optional note (e.g., “auto-save”)') ?? '') || undefined
    const today = new Date().toISOString().slice(0,10)
    try {
      // If your backend supports it, this will count as INVESTMENT when affectsBudget=true
      await api.post(`/goals/${id}/contrib`, { amount: amt, date: today, note, affectsBudget })
      await load()
    } catch (e) {
      console.error(e)
      alert('Contribution failed')
    }
  }

  const setStatus = async (id:number, value:'ACTIVE'|'PAUSED'|'COMPLETED')=>{
    try {
      await api.post(`/goals/${id}/status?value=${value}`)
      await load()
    } catch (e) {
      console.error(e)
      alert('Failed to update status')
    }
  }

  const del = async(id:number)=>{
    if (!confirm('Delete this goal (and its contributions)?')) return
    try {
      await api.delete(`/goals/${id}`)
      await load()
    } catch (e) {
      console.error(e)
      alert('Failed to delete goal')
    }
  }

  const card: React.CSSProperties = { padding:16, borderRadius:12, boxShadow:'0 1px 6px rgba(0,0,0,.08)', background:'#fff' }

  return (
    <div style={{ padding:24, maxWidth:1000, margin:'0 auto' }}>
      <h2 style={{ fontSize:22, marginBottom:12 }}>Savings Goals</h2>

      <div style={{ display:'grid', gridTemplateColumns:'2fr 1fr', gap:16 }}>
        <div style={card}>
          <div style={{ display:'flex', gap:8, flexWrap:'wrap', alignItems:'center' }}>
            <input placeholder="Goal name" value={name} onChange={e=>setName(e.target.value)} />
            <input type="number" placeholder="Target ₹" value={target} onChange={e=>setTarget(e.target.value)} />
            <input type="date" value={date} onChange={e=>setDate(e.target.value)} />
            <label className="muted" style={{ display:'flex', gap:8, alignItems:'center' }}>
              <input
                type="checkbox"
                checked={affectsBudget}
                onChange={e => setAffectsBudget(e.target.checked)}
              />
              Count future contributions as an expense (INVESTMENT)
            </label>
            <button onClick={create} disabled={loading}>Add Goal</button>
            <span style={{ marginLeft:'auto' }}>{loading ? 'Loading…' : ''}</span>
          </div>
        </div>

        <div style={card}>
          <div style={{ fontWeight:600, marginBottom:8 }}>This Month Plan</div>
          <div style={{ display:'flex', gap:8, alignItems:'center', marginBottom:8 }}>
            <input type="number" placeholder="Monthly cap (₹)" value={cap} onChange={e=>setCap(e.target.value)} />
            <button onClick={load} disabled={loading}>Recompute</button>
          </div>
          {plan && (
            <>
              <div style={{ fontSize:13, opacity:.8, marginBottom:8 }}>
                Available: ₹ {fmt(plan.available)} • Total need: ₹ {fmt(plan.totalNeed)}
              </div>
              <div style={{ display:'grid', gap:8 }}>
                {plan.items.map(it=>(
                  <div key={it.goalId} style={{ display:'flex', justifyContent:'space-between' }}>
                    <div>{it.name}</div>
                    <div>₹ {fmt(it.allocated)}</div>
                  </div>
                ))}
              </div>
            </>
          )}
        </div>
      </div>

      <div style={{ marginTop:16 }}>
        {list.map(g=>(
          <div key={g.id} style={{ ...card, marginBottom:12 }}>
            <div style={{ display:'flex', alignItems:'center', gap:12 }}>
              <div style={{ fontWeight:700 }}>{g.name}</div>
              <span style={{ fontSize:12, padding:'2px 8px', borderRadius:12, background:'#f1f1f1' }}>{g.status}</span>
              <span style={{ fontSize:12, opacity:.7 }}>Target: ₹ {fmt(g.targetAmount)} by {g.targetDate}</span>
              <span style={{ marginLeft:'auto' }}>
                <button onClick={()=>contribute(g.id)} disabled={loading}>Contribute</button>{' '}
                <button onClick={()=>setStatus(g.id, g.status==='ACTIVE'?'PAUSED':'ACTIVE')} disabled={loading}>
                  {g.status==='ACTIVE'?'Pause':'Resume'}
                </button>{' '}
                <button onClick={()=>setStatus(g.id, 'COMPLETED')} disabled={loading}>Mark Done</button>{' '}
                <button onClick={()=>del(g.id)} disabled={loading}>Delete</button>
              </span>
            </div>
            <div style={{ marginTop:8, fontSize:13 }}>
              Saved: ₹ {fmt(g.contributed)} • Remaining: ₹ {fmt(g.remaining)} • Needed/mo: ₹ {fmt(g.monthlyNeeded)} • Months left: {g.monthsLeft}
            </div>
            <BarPct pct={g.progressPct/100}/>
          </div>
        ))}
        {!list.length && <div style={{ marginTop:16 }}>No goals yet.</div>}
      </div>
    </div>
  )
}

function fmt(n:number){ return new Intl.NumberFormat().format(Math.round((n??0)*100)/100) }

function BarPct({pct}:{pct:number}){
  const w = Math.max(0, Math.min(1, pct||0))
  return (
    <div style={{ background:'#eee', height:10, borderRadius:6 }}>
      <div style={{ width:`${(w*100).toFixed(0)}%`, height:10, borderRadius:6, background:'#0e7' }} />
    </div>
  )
}
