import { AlertCircle, Package } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { api } from '../../../api'
import { PageHeader } from '../../shared/PageHeader'
import { StatCard } from '../../shared/StatCard'
import { PanelTitle } from '../../shared/PanelTitle'
import { OrderListCompact } from '../../shared/OrderListCompact'
import { DataState } from '../../shared/DataState'
import { buildDashboardStats, formatMoney } from '../../../utils'

export function DashboardPage() {
  const ordersQuery = useQuery({ queryKey: ['orders'], queryFn: () => api.orders() })
  const productsQuery = useQuery({ queryKey: ['products'], queryFn: () => api.products() })
  const orders = ordersQuery.data ?? []
  const products = productsQuery.data ?? []

  const stats = buildDashboardStats(orders, products)

  return (
    <section className="page">
      <PageHeader
        title="Tổng quan"
        subtitle="Tình hình đơn hàng và sản phẩm đang bán hôm nay."
      />
      <DataState
        isLoading={ordersQuery.isLoading || productsQuery.isLoading}
        error={ordersQuery.error || productsQuery.error}
      />
      <div className="stats-grid">
        <StatCard label="Đơn chờ giao" value={stats.pending} tone="warning" />
        <StatCard label="Chờ bàn giao" value={stats.waitingHandover} tone="info" />
        <StatCard label="Đơn hoàn tất" value={stats.completed} tone="success" />
        <StatCard label="Doanh thu hoàn tất" value={formatMoney(stats.revenue)} tone="neutral" />
        <StatCard label="Loại gạo đang bán" value={stats.activeProducts} tone="neutral" />
      </div>
      <div className="two-column">
        <section className="panel">
          <PanelTitle title="Đơn cần chú ý" icon={<AlertCircle size={18} />} />
          <OrderListCompact
            orders={orders.filter((order) => order.status !== 'COMPLETED').slice(0, 8)}
          />
        </section>
        <section className="panel">
          <PanelTitle title="Sản phẩm nổi bật" icon={<Package size={18} />} />
          <div className="product-mini-list">
            {products
              .filter((product) => product.active)
              .slice(0, 8)
              .map((product) => (
                <div key={product.id} className="mini-row">
                  <strong>{product.name}</strong>
                  <span>{formatMoney(product.price_per_kg)}/kg</span>
                </div>
              ))}
          </div>
        </section>
      </div>
    </section>
  )
}
