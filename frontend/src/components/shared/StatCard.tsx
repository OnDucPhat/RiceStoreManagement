export function StatCard({
  label,
  value,
  tone,
}: {
  label: string
  value: string | number
  tone: 'warning' | 'info' | 'success' | 'neutral'
}) {
  return (
    <article className={`stat-card ${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  )
}
