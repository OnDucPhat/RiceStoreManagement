import { type Order } from '../../api'
import { parseProductDetails } from '../../utils'
import { StatusBadge } from './StatusBadge'
import { EmptyState } from './EmptyState'

export function OrderListCompact({ orders }: { orders: Order[] }) {
  if (orders.length === 0) return <EmptyState text="Không có đơn cần chú ý." />
  return (
    <div className="compact-list">
      {orders.map((order) => (
        <div key={order.id} className="mini-row">
          <div>
            <strong>#{order.id} {order.customer_name}</strong>
            <span>{parseProductDetails(order.product_details)}</span>
          </div>
          <StatusBadge status={order.status} />
        </div>
      ))}
    </div>
  )
}
