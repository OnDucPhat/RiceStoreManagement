import { useState } from 'react'
import { ClipboardCheck, CheckCircle2, Loader2 } from 'lucide-react'
import { QRCodeCanvas } from 'qrcode.react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../../../api'
import { PageHeader } from '../../shared/PageHeader'
import { DataState } from '../../shared/DataState'
import { SelectBox } from '../../shared/form/SelectBox'
import { parseProductDetails, formatMoney, sumOrders, toggleId } from '../../../utils'

export function HandoverPage() {
  const queryClient = useQueryClient()
  const [adminId, setAdminId] = useState('')
  const [shipperId, setShipperId] = useState('')
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const adminsQuery = useQuery({ queryKey: ['users', 'ADMIN'], queryFn: () => api.users('ADMIN') })
  const shippersQuery = useQuery({ queryKey: ['users', 'SHIPPER'], queryFn: () => api.users('SHIPPER') })
  const ordersQuery = useQuery({
    queryKey: ['orders', 'DELIVERED_WAITING_HANDOVER'],
    queryFn: () => api.orders('DELIVERED_WAITING_HANDOVER'),
  })
  const orders = (ordersQuery.data ?? []).filter(
    (order) => !shipperId || order.shipper_id === Number(shipperId)
  )
  const selectedOrders = orders.filter((order) => selectedIds.includes(order.id))
  const mutation = useMutation({
    mutationFn: () =>
      api.confirmHandover({
        admin_id: Number(adminId),
        shipper_id: Number(shipperId),
        order_ids: selectedIds,
      }),
    onSuccess: () => {
      setSelectedIds([])
      queryClient.invalidateQueries({ queryKey: ['orders'] })
      queryClient.invalidateQueries({ queryKey: ['shipper-orders'] })
    },
  })
  const payload =
    shipperId && selectedIds.length > 0
      ? JSON.stringify({ shipperId: Number(shipperId), orderIds: selectedIds })
      : ''

  return (
    <section className="page">
      <PageHeader
        title="Bàn giao tiền"
        subtitle="Chốt các đơn shipper đã giao và đang giữ tiền mặt."
      />
      <DataState
        isLoading={
          adminsQuery.isLoading || shippersQuery.isLoading || ordersQuery.isLoading
        }
        error={
          adminsQuery.error ||
          shippersQuery.error ||
          ordersQuery.error ||
          mutation.error
        }
      />
      <section className="panel handover-panel">
        <div className="form-row">
          <SelectBox
            label="Admin nhận tiền"
            value={adminId}
            onChange={setAdminId}
            users={adminsQuery.data ?? []}
          />
          <SelectBox
            label="Shipper bàn giao"
            value={shipperId}
            onChange={(value) => {
              setShipperId(value)
              setSelectedIds([])
            }}
            users={shippersQuery.data ?? []}
          />
        </div>
        <div className="handover-summary">
          <span>Đã chọn {selectedIds.length} đơn</span>
          <strong>{formatMoney(sumOrders(selectedOrders))}</strong>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th></th>
                <th>Mã</th>
                <th>Khách hàng</th>
                <th>Sản phẩm</th>
                <th>Tổng tiền</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((order) => (
                <tr key={order.id}>
                  <td>
                    <input
                      type="checkbox"
                      checked={selectedIds.includes(order.id)}
                      onChange={() =>
                        setSelectedIds((current) => toggleId(current, order.id))
                      }
                    />
                  </td>
                  <td>#{order.id}</td>
                  <td>
                    <strong>{order.customer_name}</strong>
                    <small>{order.customer_phone}</small>
                  </td>
                  <td>{parseProductDetails(order.product_details)}</td>
                  <td>{formatMoney(order.total_price)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {orders.length === 0 ? (
          <div className="empty-state">Không có đơn chờ bàn giao cho shipper này.</div>
        ) : null}
        <div className="handover-footer">
          <div className="qr-preview">
            {payload ? (
              <QRCodeCanvas value={payload} size={132} />
            ) : (
              <ClipboardCheck size={48} />
            )}
          </div>
          <button
            className="button primary"
            type="button"
            disabled={
              !adminId || !shipperId || selectedIds.length === 0 || mutation.isPending
            }
            onClick={() => mutation.mutate()}
          >
            {mutation.isPending ? (
              <Loader2 className="spin" size={18} />
            ) : (
              <CheckCircle2 size={18} />
            )}{' '}
            Xác nhận bàn giao
          </button>
        </div>
      </section>
    </section>
  )
}
