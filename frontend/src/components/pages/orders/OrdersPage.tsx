import { useState } from 'react'
import { Plus, Search } from 'lucide-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api, type Order, type OrderStatus } from '../../../api'
import { PageHeader } from '../../shared/PageHeader'
import { StatusBadge } from '../../shared/StatusBadge'
import { DataState } from '../../shared/DataState'
import { EmptyState } from '../../shared/EmptyState'
import { OrderDetailDrawer } from '../../shared/OrderDetailDrawer'
import { CreateOrderModal } from './CreateOrderModal'
import { CreateRetailOrderModal } from './CreateRetailOrderModal'
import { orderStatuses, statusLabels, sourceLabels } from '../../../constants'
import { filterOrders, parseProductDetails, formatMoney } from '../../../utils'

export function OrdersPage() {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<OrderStatus | 'ALL'>('ALL')
  const [search, setSearch] = useState('')
  const [selectedOrder, setSelectedOrder] = useState<Order | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [showRetailCreate, setShowRetailCreate] = useState(false)
  const [shipperByOrder, setShipperByOrder] = useState<Record<number, string>>({})
  const ordersQuery = useQuery({
    queryKey: ['orders', status],
    queryFn: () => api.orders(status === 'ALL' ? undefined : status),
  })
  const shippersQuery = useQuery({ queryKey: ['users', 'SHIPPER'], queryFn: () => api.users('SHIPPER') })
  const assignMutation = useMutation({
    mutationFn: ({ orderId, shipperId }: { orderId: number; shipperId: number }) =>
      api.assignShipper(orderId, shipperId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['orders'] })
      queryClient.invalidateQueries({ queryKey: ['shipper-orders'] })
    },
  })
  const orders = filterOrders(ordersQuery.data ?? [], search)

  return (
    <section className="page">
      <PageHeader
        title="Đơn hàng"
        subtitle="Tạo đơn, lọc trạng thái, xem chi tiết và gán shipper."
        action={
          <div className="action-group">
            <button
              className="button primary"
              type="button"
              onClick={() => setShowRetailCreate(true)}
            >
              <Plus size={18} /> Tạo đơn tại quầy
            </button>
            <button
              className="button secondary"
              type="button"
              onClick={() => setShowCreate(true)}
            >
              <Plus size={18} /> Tạo đơn online
            </button>
          </div>
        }
      />
      <div className="toolbar">
        <div className="segmented">
          <button
            className={status === 'ALL' ? 'active' : ''}
            onClick={() => setStatus('ALL')}
            type="button"
          >
            Tất cả
          </button>
          {orderStatuses.map((item) => (
            <button
              key={item}
              className={status === item ? 'active' : ''}
              onClick={() => setStatus(item)}
              type="button"
            >
              {statusLabels[item]}
            </button>
          ))}
        </div>
        <label className="search-box">
          <Search size={18} />
          <input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Tìm tên, SĐT, địa chỉ..."
          />
        </label>
      </div>
      <DataState isLoading={ordersQuery.isLoading} error={ordersQuery.error || assignMutation.error} />
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Mã</th>
              <th>Khách hàng</th>
              <th>Sản phẩm</th>
              <th>Tổng tiền</th>
              <th>Nguồn</th>
              <th>Trạng thái</th>
              <th>Shipper</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {orders.map((order) => (
              <tr key={order.id}>
                <td>#{order.id}</td>
                <td>
                  <strong>{order.customer_name}</strong>
                  <small>{order.customer_phone}</small>
                  <small>{order.address}</small>
                </td>
                <td>{parseProductDetails(order.product_details)}</td>
                <td>{formatMoney(order.total_price)}</td>
                <td>{sourceLabels[order.source]}</td>
                <td>
                  <StatusBadge status={order.status} />
                </td>
                <td>
                  <div className="assign-control">
                    <select
                      value={String(order.shipper_id ?? shipperByOrder[order.id] ?? '')}
                      disabled={order.status !== 'PENDING' || assignMutation.isPending}
                      onChange={(event) => {
                        const shipperId = event.target.value
                        setShipperByOrder((current) => ({ ...current, [order.id]: shipperId }))
                        if (shipperId) {
                          assignMutation.mutate({ orderId: order.id, shipperId: Number(shipperId) })
                        }
                      }}
                    >
                      <option value="">Chọn shipper</option>
                      {(shippersQuery.data ?? []).map((shipper) => (
                        <option key={shipper.id} value={shipper.id}>
                          {shipper.username}
                        </option>
                      ))}
                    </select>
                  </div>
                </td>
                <td>
                  <button
                    className="button ghost"
                    type="button"
                    onClick={() => setSelectedOrder(order)}
                  >
                    Chi tiết
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {orders.length === 0 && !ordersQuery.isLoading ? (
        <EmptyState text="Chưa có đơn phù hợp." />
      ) : null}
      {showCreate ? <CreateOrderModal onClose={() => setShowCreate(false)} /> : null}
      {showRetailCreate ? (
        <CreateRetailOrderModal onClose={() => setShowRetailCreate(false)} />
      ) : null}
      {selectedOrder ? (
        <OrderDetailDrawer order={selectedOrder} onClose={() => setSelectedOrder(null)} />
      ) : null}
    </section>
  )
}
