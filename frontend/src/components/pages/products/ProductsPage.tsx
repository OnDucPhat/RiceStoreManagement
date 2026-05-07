import { useState } from 'react'
import { Plus } from 'lucide-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api, type RiceProduct } from '../../../api'
import { PageHeader } from '../../shared/PageHeader'
import { DataState } from '../../shared/DataState'
import { EmptyState } from '../../shared/EmptyState'
import { ProductModal } from './ProductModal'
import { formatMoney } from '../../../utils'

export function ProductsPage() {
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState<RiceProduct | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const productsQuery = useQuery({ queryKey: ['products'], queryFn: () => api.products() })
  const activeMutation = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      api.setProductActive(id, active),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['products'] }),
  })

  return (
    <section className="page">
      <PageHeader
        title="Loại gạo"
        subtitle="Quản lý giá bán, giá vốn, lợi nhuận và trạng thái bán."
        action={
          <button
            className="button primary"
            type="button"
            onClick={() => setShowCreate(true)}
          >
            <Plus size={18} /> Thêm loại gạo
          </button>
        }
      />
      <DataState
        isLoading={productsQuery.isLoading}
        error={productsQuery.error || activeMutation.error}
      />
      <div className="product-grid">
        {(productsQuery.data ?? []).map((product) => (
          <article key={product.id} className="product-card">
            <div className="card-row">
              <h3>{product.name}</h3>
              <span className={`pill ${product.active ? 'pill-green' : 'pill-muted'}`}>
                {product.active ? 'Đang bán' : 'Tạm ẩn'}
              </span>
            </div>
            <p>{product.characteristics}</p>
            <div className="price-grid">
              <span>
                Giá bán <strong>{formatMoney(product.price_per_kg)}</strong>
              </span>
              <span>
                Giá vốn <strong>{formatMoney(product.cost_per_kg)}</strong>
              </span>
              <span>
                Lãi/kg <strong>{formatMoney(product.profit_per_kg)}</strong>
              </span>
              <span>
                Tồn kho <strong>{Number(product.stock_kg ?? 0).toFixed(1)} kg</strong>
              </span>
            </div>
            <div className="card-actions">
              <button
                className="button ghost"
                type="button"
                onClick={() => setEditing(product)}
              >
                Sửa
              </button>
              <button
                className="button ghost"
                type="button"
                disabled={activeMutation.isPending}
                onClick={() => activeMutation.mutate({ id: product.id, active: !product.active })}
              >
                {product.active ? 'Tạm ẩn' : 'Bật bán'}
              </button>
            </div>
          </article>
        ))}
      </div>
      {(productsQuery.data ?? []).length === 0 && !productsQuery.isLoading ? (
        <EmptyState text="Chưa có loại gạo." />
      ) : null}
      {showCreate ? <ProductModal onClose={() => setShowCreate(false)} /> : null}
      {editing ? (
        <ProductModal product={editing} onClose={() => setEditing(null)} />
      ) : null}
    </section>
  )
}
