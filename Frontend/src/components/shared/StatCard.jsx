export function StatCard({ label, value }) {
  return (
    <div className="flex-1 rounded-xl border border-bk-border bg-bk-surface p-[18px]">
      <p className="mb-1.5 text-[11px] font-bold uppercase tracking-[0.06em] text-bk-muted">{label}</p>
      <p className="text-[26px] font-extrabold text-bk-text">{value}</p>
    </div>
  )
}
