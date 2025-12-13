import React, { useEffect, useState } from 'react'
import api from '../api'

type BillView = {
  id: number; name: string; category: string; amount: number;
  dueDay: number; leadDays: number; active: boolean; nextDueDate: string
}

export default function Bills(){
  const [list, setList] = useState<BillView[]>([])
  const [name, setName] = useState('')
  const [category, setCategory] = useState('UTILITIES')
  const [amount, setAmount] = useState<number>()
  const [dueDay, setDueDay] = useState<number>(5)
  const [leadDays, setLeadDays] = useState<number>(3)
  const [loading, setLoading] = useState(false)

  const load = async ()=>{
    setLoading(true)
    try { const { data } = await api.get<BillView[]>('/bills'); setList(data) }
    finally { setLoading(false) }
  }
  useEffect(()=>{ load() }, [])

  const add = async ()=>{
    if (!name || !amount || !dueDay) return alert('Fill name, amount, due day')
    await api.post('/bills', { name, category, amount, dueDay, leadDays })
    setName(''); setAmount(0); setDueDay(5); setLeadDays(3)
    await load()
  }

  const toggleActive = async (b:BillView)=>{
    await api.put(`/bills/${b.id}`, { active: !b.active })
    await load()
  }

  const remove = async (id:number)=>{
    if (!confirm('Delete this bill?')) return
    await api.delete(`/bills/${id}`); await load()
  }

  const preview = async ()=>{ await api.post('/bills/send-preview'); alert('Preview sent (Mailhog)') }
  const runToday = async ()=>{ const { data } = await api.post('/bills/run-today'); alert('Emails sent: '+data.emails) }

  const card: React.CSSProperties = { padding:16, borderRadius:12, boxShadow:'0 1px 6px rgba(0,0,0,.08)', background:'#fff' }

  return (
    <div style={{ padding:24, maxWidth:1000, margin:'0 auto' }}>
      <h2 style={{ fontSize:22, marginBottom:12 }}>Bill Reminders</h2>

      <div style={{ display:'grid', gridTemplateColumns:'2fr 1fr', gap:16 }}>
        <div style={card}>
          <div style={{ display:'flex', gap:8, flexWrap:'wrap', alignItems:'center' }}>
            <input placeholder="Bill name" value={name} onChange={e=>setName(e.target.value)} />
            <select value={category} onChange={e=>setCategory(e.target.value)}>
              {['HOUSING','UTILITIES','TRANSPORT','FOOD','ENTERTAINMENT','HEALTH','INVESTMENT','OTHER'].map(c=>
                <option key={c} value={c}>{c}</option>)}
            </select>
            <input type="number" placeholder="Amount ₹" value={amount === 0 ? '' : amount || ''} onChange={e => setAmount(e.target.value ? Number(e.target.value) : 0)} />
            <input type="number" placeholder="Due" value={dueDay === 0 ? '' : dueDay || ''} min={1} max={28} onChange={e => setDueDay(e.target.value ? Number(e.target.value) : 0)} />
            <input type="number" placeholder="Lead" value={leadDays === 0 ? '' : leadDays || ''} min={0} max={10} onChange={e => setLeadDays(e.target.value ? Number(e.target.value) : 0)} />
            <button onClick={add}>Add Bill</button>
            <span style={{ marginLeft:'auto' }}>{loading ? 'Loading…' : ''}</span>
          </div>
        </div>

        <div style={card}>
          <div style={{ fontWeight:600, marginBottom:8 }}>Email Tools</div>
          <div style={{ display:'flex', gap:8 }}>
            <button onClick={preview}>Send Preview</button>
            <button onClick={runToday}>Run Today Check</button>
          </div>
          <div style={{ fontSize:12, opacity:.75, marginTop:8 }}>
            Mailhog UI: <a href="http://localhost:8025" target="_blank" rel="noreferrer">http://localhost:8025</a>
          </div>
        </div>
      </div>

      <div style={{ marginTop:16 }}>
        {list.map(b=>(
          <div key={b.id} style={{ ...card, marginBottom:12 }}>
            <div style={{ display:'flex', alignItems:'center', gap:12 }}>
              <div style={{ fontWeight:700 }}>{b.name}</div>
              <span style={{ fontSize:12, padding:'2px 8px', borderRadius:12, background:'#f1f1f1' }}>{b.category}</span>
              <span>₹ {fmt(b.amount)}</span>
              <span>Due: day {b.dueDay} (next {b.nextDueDate})</span>
              <span>Lead: {b.leadDays}d</span>
              <span style={{ marginLeft:'auto' }}>
                <button onClick={()=>toggleActive(b)}>{b.active ? 'Pause' : 'Resume'}</button>{' '}
                <button onClick={()=>remove(b.id)}>Delete</button>
              </span>
            </div>
          </div>
        ))}
        {!list.length && <div style={{ marginTop:16 }}>No bills yet.</div>}
      </div>
    </div>
  )
}
function fmt(n:number){ return new Intl.NumberFormat().format(Math.round((n??0)*100)/100) }
