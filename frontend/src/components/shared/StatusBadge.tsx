import { type OrderStatus } from '../../api'
import { statusLabels } from '../../constants'

export function StatusBadge({ status }: { status: OrderStatus }) {
  return <span className={`status ${status.toLowerCase()}`}>{statusLabels[status]}</span>
}
