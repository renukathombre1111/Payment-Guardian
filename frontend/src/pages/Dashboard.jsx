import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, inr } from '../api.js'
import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

const tone = {
  CRITICAL: 'text-rose-500',
  HIGH: 'text-rose-400',
  MEDIUM: 'text-amber-400',
  LOW: 'text-emerald-400',
  INFO: 'text-slate-400',
}

const decisionTone = {
  APPROVE: 'text-emerald-400',
  REVIEW: 'text-amber-300',
  BLOCK: 'text-rose-400',
}

export default function Dashboard() {
  const [stats, setStats] = useState(null)
  const [allCases, setAllCases] = useState([])
  const [paymentCase, setPaymentCase] = useState(null)
  const [detail, setDetail] = useState(null)
  const [scenarios, setScenarios] = useState([])
  const [chat, setChat] = useState('')
  const [answer, setAnswer] = useState('')
  const [message, setMessage] = useState('')
  const navigate = useNavigate()

  useEffect(() => {
    async function load() {
      const dash = await api.get('/dashboard')
      setStats(dash.data)
      const cases = await api.get('/cases')
      setAllCases(cases.data)
      const reviewCase = cases.data.find((c) => c.decision === 'REVIEW') || cases.data[0]
      setPaymentCase(reviewCase)
      if (reviewCase) {
        const d = await api.get(`/cases/${reviewCase.id}`)
        setDetail(d.data)
        const sim = await api.post('/simulate-payment', {
          vendorId: reviewCase.vendorId,
          amount: reviewCase.amount,
          date: '2026-08-27',
        })
        setScenarios(sim.data.scenarios)
      }
    }
    load().catch((e) => setMessage(e.message))
  }, [])

  async function selectCase(c) {
    setPaymentCase(c)
    const d = await api.get(`/cases/${c.id}`)
    setDetail(d.data)
    const sim = await api.post('/simulate-payment', {
      vendorId: c.vendorId,
      amount: c.amount,
      date: '2026-08-27',
    })
    setScenarios(sim.data.scenarios)
    setMessage('')
    setAnswer('')
  }

  async function act(path) {
    if (!paymentCase) return
    const res = await api.post(`/cases/${paymentCase.id}/${path}`, { note: 'Dashboard action', actor: 'demo-user' })
    setMessage(res.data.message)
    const cases = await api.get('/cases')
    setAllCases(cases.data)
    setPaymentCase(cases.data.find((c) => c.id === paymentCase.id))
  }

  async function ask(e) {
    e.preventDefault()
    if (!paymentCase || !chat.trim()) return
    const res = await api.post(`/cases/${paymentCase.id}/chat`, { message: chat })
    setAnswer(res.data.answer)
  }

  if (!stats || !detail) {
    return <p className="p-6 text-slate-400">{message || 'Loading NovaTech books…'}</p>
  }

  return (
    <main className="p-6 grid gap-6 lg:grid-cols-3">
      <section className="lg:col-span-3 grid grid-cols-2 lg:grid-cols-4 gap-4">
        <Stat label="Cash Position" value={inr(stats.cashPosition)} />
        <Stat label="Today's Payments" value={inr(stats.todaysPayments)} />
        <Stat label="High Risk" value={stats.highRisk} />
        <Stat label="Awaiting Review" value={stats.awaitingReview} />
      </section>

      <section className="lg:col-span-3 rounded-2xl border border-line bg-panel p-4">
        <h3 className="text-sm font-medium text-slate-400 mb-3">Demo scenarios (synthetic data)</h3>
        <div className="flex flex-wrap gap-2">
          {allCases.map((c) => (
            <button
              key={c.id}
              onClick={() => selectCase(c)}
              className={`rounded-lg border px-3 py-2 text-sm ${paymentCase?.id === c.id ? 'border-emerald-400 bg-emerald-500/10' : 'border-line'}`}
            >
              Case #{c.id} · {c.decision} · {inr(c.amount)}
            </button>
          ))}
        </div>
      </section>

      <section className="lg:col-span-2 rounded-2xl border border-line bg-panel p-6">
        <p className={`font-medium ${decisionTone[detail.decision] || 'text-amber-300'}`}>
          {detail.decision === 'APPROVE' ? '✓' : detail.decision === 'BLOCK' ? '⛔' : '⚠'} Payment {detail.decision}
        </p>
        <h2 className="mt-2 text-2xl font-semibold">{detail.context.vendor}</h2>
        <p className="text-slate-300">{inr(detail.context.amount)}</p>
        <div className="mt-4 flex flex-wrap gap-6 text-sm">
          <div>Risk Score <span className={`font-semibold ${tone[detail.riskBand] || 'text-rose-400'}`}>{detail.riskScore} / 100</span></div>
          <div>Band <span className="font-semibold">{detail.riskBand}</span></div>
          <div>Decision <span className={`font-semibold ${decisionTone[detail.decision]}`}>{detail.decision}</span></div>
        </div>
        <ul className="mt-5 space-y-2">
          {detail.reasons.map((r) => (
            <li key={r.reason} className={tone[r.severity] || 'text-slate-300'}>
              {r.severity === 'CRITICAL' || r.severity === 'HIGH' ? '🔴' : r.severity === 'MEDIUM' ? '🟠' : '🟢'} {r.reason}
            </li>
          ))}
        </ul>

        {detail.riskSignals?.length > 0 && (
          <div className="mt-5">
            <h4 className="text-sm text-slate-400 mb-2">12 risk signals</h4>
            <div className="grid gap-1 text-xs">
              {detail.riskSignals.map((s) => (
                <div key={s.name} className="flex justify-between border-b border-line/50 py-1">
                  <span className={tone[s.severity]}>{s.name}</span>
                  <span className="text-slate-500">+{s.contribution} · {s.severity}</span>
                </div>
              ))}
            </div>
          </div>
        )}

        <div className="mt-6 flex flex-wrap gap-3">
          <button onClick={() => navigate(`/evidence/${paymentCase.id}`)} className="rounded-lg bg-slate-100 text-ink px-4 py-2 text-sm font-medium">View evidence</button>
          <button onClick={() => act('approve')} className="rounded-lg bg-emerald-500 text-ink px-4 py-2 text-sm font-medium">Approve</button>
          <button onClick={() => act('hold')} className="rounded-lg border border-amber-400 text-amber-200 px-4 py-2 text-sm font-medium">Hold</button>
          <button onClick={() => act('reject')} className="rounded-lg border border-rose-400 text-rose-300 px-4 py-2 text-sm font-medium">Reject</button>
          <button onClick={() => act('escalate')} className="rounded-lg border border-line text-slate-300 px-4 py-2 text-sm font-medium">Escalate</button>
        </div>
        {message && <p className="mt-4 text-sm text-emerald-300">{message}</p>}
        <p className="mt-3 text-xs text-slate-500">Status: {paymentCase.status} · Human approval required · No money moved</p>
      </section>

      <section className="rounded-2xl border border-line bg-panel p-6 space-y-4">
        <div>
          <h3 className="font-medium">AI Investigator</h3>
          <form onSubmit={ask} className="mt-3 flex gap-2">
            <input value={chat} onChange={(e) => setChat(e.target.value)} placeholder="Why did you stop this payment?" className="flex-1 rounded-lg bg-ink border border-line px-3 py-2 text-sm" />
            <button className="rounded-lg bg-emerald-500 text-ink px-3 text-sm font-medium">Ask</button>
          </form>
          {answer && <p className="mt-4 text-sm leading-6 text-slate-200">{answer}</p>}
          <div className="mt-4 flex flex-wrap gap-2 text-xs">
            {['Why did you stop this payment?', 'What if I pay tomorrow?', 'What if I split it?'].map((q) => (
              <button key={q} onClick={() => setChat(q)} className="rounded-full border border-line px-3 py-1 text-slate-400">{q}</button>
            ))}
          </div>
        </div>

        {detail.safeAlternatives?.length > 0 && (
          <div>
            <h3 className="font-medium text-emerald-400">Safer alternatives</h3>
            <ul className="mt-2 space-y-2 text-sm">
              {detail.safeAlternatives.map((a) => (
                <li key={a.id} className="rounded-lg border border-line p-2">
                  <p className="font-medium">{a.title}</p>
                  <p className="text-slate-400 text-xs mt-1">{a.description}</p>
                </li>
              ))}
            </ul>
          </div>
        )}
      </section>

      <section className="lg:col-span-3 rounded-2xl border border-line bg-panel p-6">
        <div className="flex items-center justify-between">
          <h3 className="font-medium">Simulation — what if this payment happens?</h3>
          <Link to={`/evidence/${paymentCase.id}`} className="text-sm text-emerald-400">Open evidence graph</Link>
        </div>
        <div className="h-64 mt-4">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={scenarios}>
              <XAxis dataKey="name" stroke="#94a3b8" tick={{ fontSize: 11 }} />
              <YAxis stroke="#94a3b8" />
              <Tooltip formatter={(v) => inr(v)} />
              <Bar dataKey="minimumCash" fill="#34d399" />
            </BarChart>
          </ResponsiveContainer>
        </div>
        <div className="mt-4 grid gap-3 md:grid-cols-5">
          {scenarios.map((s) => (
            <div key={s.id} className="rounded-xl border border-line p-3">
              <p className="text-sm font-medium">{s.name}</p>
              <p className="text-emerald-300">{inr(s.minimumCash)} min cash</p>
              <p className={`text-xs mt-1 ${tone[s.risk]}`}>{s.risk}</p>
              <p className="text-xs text-slate-400 mt-2">{s.note}</p>
            </div>
          ))}
        </div>
      </section>
    </main>
  )
}

function Stat({ label, value }) {
  return (
    <div className="rounded-2xl border border-line bg-panel p-4">
      <p className="text-xs uppercase tracking-widest text-slate-400">{label}</p>
      <p className="mt-2 text-2xl font-semibold">{value}</p>
    </div>
  )
}
