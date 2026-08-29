import { NavLink, Route, Routes } from 'react-router-dom'
import Dashboard from './pages/Dashboard.jsx'
import Evidence from './pages/Evidence.jsx'
import Evaluation from './pages/Evaluation.jsx'

export default function App() {
  return (
    <div className="min-h-screen">
      <header className="border-b border-line px-6 py-4 flex items-center justify-between bg-panel/80">
        <div>
          <p className="text-xs uppercase tracking-[0.3em] text-emerald-400">Payment Guardian</p>
          <h1 className="text-lg font-semibold">AI Financial Decision Safety Layer</h1>
        </div>
        <nav className="flex gap-4 text-sm">
          <NavLink to="/" className={({ isActive }) => isActive ? 'text-emerald-400' : 'text-slate-400'}>Dashboard</NavLink>
          <NavLink to="/evidence" className={({ isActive }) => isActive ? 'text-emerald-400' : 'text-slate-400'}>Evidence</NavLink>
          <NavLink to="/evaluation" className={({ isActive }) => isActive ? 'text-emerald-400' : 'text-slate-400'}>Evaluation</NavLink>
        </nav>
      </header>
      <p className="mx-6 mt-4 rounded-md border border-amber-500/40 bg-amber-500/10 px-4 py-2 text-sm text-amber-200">
        AI recommendation — human approval required. Payment Guardian never moves money automatically.
      </p>
      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/evidence" element={<Evidence />} />
        <Route path="/evidence/:id" element={<Evidence />} />
        <Route path="/evaluation" element={<Evaluation />} />
      </Routes>
    </div>
  )
}
