import React, { useEffect, useState } from 'react'
import api from '../api'

type Alert = { id:number; type:string; severity:string; title:string; message:string; txId:number|null; createdAt:string; read:boolean }

export default function Alerts(){
  const [items, setItems] = useState<Alert[]>([])
  const [loading, setLoading] = useState(false)

  const load = async ()=>{
    setLoading(true)
    try {
      const { data } = await api.get<Alert[]>('/alerts')
      setItems(data)
    } finally { setLoading(false) }
  }
  useEffect(()=>{ load() }, [])

  const markRead = async (id:number)=>{
    await api.post(`/alerts/${id}/read`)
    await load()
  }

  const pill = (sev:string)=>({
    padding:'2px 8px', borderRadius:12, fontSize:12,
    background: sev==='HIGH' ? '#ffe6e6' : sev==='MEDIUM' ? '#fff4e6' : '#eef5ff'
  } as React.CSSProperties)

  return (
    <div style={{ padding:24, maxWidth:900, margin:'0 auto' }}>
      <h2 style={{ fontSize:22, marginBottom:12 }}>Alerts</h2>
      {loading ? 'Loading…' : (
        <div style={{ display:'flex', flexDirection:'column', gap:12 }}>
          {items.map(a=>(
            <div key={a.id} style={{ padding:12, borderRadius:10, boxShadow:'0 1px 6px rgba(0,0,0,.08)', background:'#fff' }}>
              <div style={{ display:'flex', justifyContent:'space-between', alignItems:'center' }}>
                <div>
                  <span style={pill(a.severity)}>{a.severity}</span>
                  <span style={{ marginLeft:10, fontWeight:600 }}>{a.title}</span>
                  <div style={{ fontSize:13, opacity:.8, marginTop:4 }}>{a.message}</div>
                </div>
                <div>
                  {!a.read && <button onClick={()=>markRead(a.id)}>Mark read</button>}
                </div>
              </div>
            </div>
          ))}
          {!items.length && <div>No alerts yet.</div>}
        </div>
      )}
    </div>
  )
}
