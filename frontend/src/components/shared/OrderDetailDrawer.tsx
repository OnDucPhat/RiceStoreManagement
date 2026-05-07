import { type Order } from '../../api'
import { parseProductDetails, formatMoney } from '../../utils'
import { StatusBadge } from './StatusBadge'
import { sourceLabels } from '../../constants'

export function OrderDetailDrawer({ order, onClose }: { order: Order; onClose: () => void }) {
  return (
    <div className="drawer-backdrop" role="presentation" onMouseDown={onClose}>
      <aside
        className="drawer"
        role="dialog"
        aria-modal="true"
        aria-label="Chi tiết đơn hàng"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <div className="modal-header">
          <h2>Đơn #{order.id}</h2>
          <button className="icon-button" type="button" onClick={onClose} aria-label="Đóng">
            x
          </button>
        </div>
        <dl className="details-list">
          <dt>Khách hàng</dt>
          <dd>{order.customer_name}</dd>
          <dt>SĐT</dt>
          <dd>{order.customer_phone}</dd>
          <dt>Địa chỉ</dt>
          <dd>{order.address}</dd>
          <dt>Sản phẩm</dt>
          <dd>{parseProductDetails(order.product_details)}</dd>
          <dt>Chi tiết gốc</dt>
          <dd>{order.product_details}</dd>
          <dt>Tổng tiền</dt>
          <dd>{formatMoney(order.total_price)}</dd>
          <dt>Nguồn</dt>
          <dd>{sourceLabels[order.source]}</dd>
          <dt>Trạng thái</dt>
          <dd><StatusBadge status={order.status} /></dd>
          <dt>Shipper ID</dt>
          <dd>{order.shipper_id ?? 'Chưa gán'}</dd>
        </dl>
      </aside>
    </div>
  )
}
