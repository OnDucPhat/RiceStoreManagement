export function PanelTitle({ title, icon }: { title: string; icon: React.ReactNode }) {
  return (
    <div className="panel-title">
      {icon}
      <h2>{title}</h2>
    </div>
  )
}
