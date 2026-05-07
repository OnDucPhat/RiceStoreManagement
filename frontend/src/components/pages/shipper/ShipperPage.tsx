import { NavLink } from 'react-router-dom'
import { Truck, LogOut, ClipboardCheck, Loader2 } from 'lucide-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api, type User, type OrderStatus } from '../../../api'
import { StatusBadge } from '../../shared/StatusBadge'
import { DataState } from '../../shared/DataState'
import { SelectBox } from '../../shared/form/SelectBox'
import { parseProductDetails, formatMoney, sumOrders } from '../../../utils'
import { useState } from 'react'
import { QRCodeCanvas } from 'qrcode.react'

function LogoutButton() {
  const queryClient = useQueryClient()
  const mutation = useMutation({
    mutationFn: api.logout,
    onSettled: () => {
      queryClient.setQueryData(['auth', 'me'], null)
      queryClient.removeQueries({ queryKey: ['orders'] })
      queryClient.removeQueries({ queryKey: ['products'] })
      queryClient.removeQueries({ queryKey: ['users'] })
      queryClient.removeQueries({ queryKey: ['shipper-orders'] })
    },
  })
  return (
    <button
      className="button ghost"
      type="button"
      onClick={() => mutation.mutate()}
      disabled={mutation.isPending}
    >
      <LogOut size={18} /> Dang xuat
    </button>
  )
}

export function ShipperPage({ user }: { user: User | null }) {
  const queryClient = useQueryClient()
  const [shipperId, setShipperId] = useState(() =>
    user?.role === 'SHIPPER' ? String(user.id) : ''
  )
  const [status, setStatus] = useState<OrderStatus | 'ALL'>('ALL')
  const shippersQuery = useQuery({
    queryKey: ['users', 'SHIPPER'],
    queryFn: () => api.users('SHIPPER'),
    enabled: user?.role === 'ADMIN',
  })
  const visibleShippers: User[] =
    user?.role === 'ADMIN' ? (shippersQuery.data ?? []) : user ? [user] : []
  const selectedShipperId = Number(shipperId)
  const ordersQuery = useQuery({
    queryKey: ['shipper-orders', selectedShipperId, status],
    queryFn: () => api.shipperOrders(selectedShipperId, status === 'ALL' ? undefined : status),
    enabled: Boolean(selectedShipperId),
  })
  const deliverMutation = useMutation({
    mutationFn: (orderId: number) => api.markDelivered(orderId, selectedShipperId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['shipper-orders'] })
      queryClient.invalidateQueries({ queryKey: ['orders'] })
    },
  })
  const orders = ordersQuery.data ?? []
  const deliveredIds = orders
    .filter((order) => order.status === 'DELIVERED_WAITING_HANDOVER')
    .map((order) => order.id)
  const qrPayload =
    selectedShipperId && deliveredIds.length
      ? JSON.stringify({ shipperId: selectedShipperId, orderIds: deliveredIds })
      : ''
  const shipperPickerDisabled = user?.role !== 'ADMIN'

  return (
    <main className="shipper-shell">
      <header className="shipper-header">
        <div>
          <h1>Giao hàng</h1>
          <p>Danh sách đơn được phân công cho shipper.</p>
        </div>
        {user?.role === 'ADMIN' ? (
          <NavLink className="button ghost" to="/admin">
            Admin
          </NavLink>
        ) : (
          <LogoutButton />
        )}
      </header>
      <section className="shipper-control">
        <SelectBox
          label="Chọn shipper"
          value={shipperId}
          onChange={setShipperId}
          users={visibleShippers}
          disabled={shipperPickerDisabled}
        />
        <div className="segmented full">
          <button
            className={status === 'ALL' ? 'active' : ''}
            onClick={() => setStatus('ALL')}
            type="button"
          >
            Tất cả
          </button>
          <button
            className={status === 'PENDING' ? 'active' : ''}
            onClick={() => setStatus('PENDING')}
            type="button"
          >
            Chờ giao
          </button>
          <button
            className={status === 'DELIVERED_WAITING_HANDOVER' ? 'active' : ''}
            onClick={() => setStatus('DELIVERED_WAITING_HANDOVER')}
            type="button"
          >
            Đã giao
          </button>
        </div>
      </section>
      <DataState
        isLoading={ordersQuery.isLoading || shippersQuery.isLoading}
        error={ordersQuery.error || shippersQuery.error || deliverMutation.error}
      />
      <section className="delivery-list">
        {orders.map((order) => (
          <article key={order.id} className="delivery-card">
            <div className="card-row">
              <h2>Đơn #{order.id}</h2>
              <StatusBadge status={order.status} />
            </div>
            <p>
              <strong>{order.customer_name}</strong> - {order.customer_phone}
            </p>
            <p>{order.address}</p>
            <p>{parseProductDetails(order.product_details)}</p>
            <strong>{formatMoney(order.total_price)}</strong>
            <button
              className="button primary block touch"
              type="button"
              disabled={order.status !== 'PENDING' || deliverMutation.isPending}
              onClick={() => deliverMutation.mutate(order.id)}
            >
              {deliverMutation.isPending ? (
                <Loader2 className="spin" size={22} />
              ) : (
                <Truck size={22} />
              )}{' '}
              Đã giao và đã thu tiền
            </button>
          </article>
        ))}
      </section>
      {selectedShipperId && orders.length === 0 && !ordersQuery.isLoading ? (
        <div className="empty-state">Shipper này chưa có đơn.</div>
      ) : null}
      <section className="handover-mobile">
        <h2>QR bàn giao cuối ca</h2>
        <p>
          {deliveredIds.length} đơn đang chờ bàn giao, tổng{' '}
          {formatMoney(sumOrders(orders.filter((order) => deliveredIds.includes(order.id))))}
        </p>
        <div className="qr-large">
          {qrPayload ? <QRCodeCanvas value={qrPayload} size={220} /> : <ClipboardCheck size={72} />}
        </div>
      </section>
    </main>
  )
}
