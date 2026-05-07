import { useState } from 'react'
import { Archive, Warehouse } from 'lucide-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api, type StockEntry } from '../../../api'
import { PageHeader } from '../../shared/PageHeader'
import { PanelTitle } from '../../shared/PanelTitle'
import { DataState } from '../../shared/DataState'
import { MutationError } from '../../shared/MutationError'
import { formatMoney } from '../../../utils'

export function InventoryPage() {
  const queryClient = useQueryClient()
  const productsQuery = useQuery({ queryKey: ['products'], queryFn: () => api.products() })
  const products = productsQuery.data ?? []
  const [selectedProductId, setSelectedProductId] = useState('')
  const [qtyKg, setQtyKg] = useState('')
  const [costPerKg, setCostPerKg] = useState('')
  const [historyProductId, setHistoryProductId] = useState<number | null>(null)
  const historyQuery = useQuery({
    queryKey: ['stock-history', historyProductId],
    queryFn: () => api.stockHistory(historyProductId!),
    enabled: historyProductId !== null,
  })

  const selectedProduct = products.find((p) => p.id === Number(selectedProductId)) ?? null
  const previewNewCost = (() => {
    if (!selectedProduct || !qtyKg || !costPerKg) return null
    const oldStock = Number(selectedProduct.stock_kg ?? 0)
    const oldCost = Number(selectedProduct.cost_per_kg)
    const qty = Number(qtyKg)
    const newCostImport = Number(costPerKg)
    if (qty <= 0) return null
    if (oldStock === 0) return newCostImport
    return (oldStock * oldCost + qty * newCostImport) / (oldStock + qty)
  })()

  const importMutation = useMutation({
    mutationFn: () =>
      api.importStock({
        product_id: Number(selectedProductId),
        quantity_kg: Number(qtyKg),
        cost_per_kg: Number(costPerKg),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['products'] })
      queryClient.invalidateQueries({ queryKey: ['stock-history', Number(selectedProductId)] })
      setQtyKg('')
      setCostPerKg('')
    },
  })

  return (
    <section className="page">
      <PageHeader
        title="Nhập kho"
        subtitle="Nhập hàng và theo dõi tồn kho từng loại gạo."
      />
      <DataState isLoading={productsQuery.isLoading} error={productsQuery.error} />
      <div className="two-column">
        <section className="panel">
          <PanelTitle title="Nhập hàng" icon={<Archive size={18} />} />
          <div className="form-grid single">
            <label className="field">
              <span>Loại gạo</span>
              <select
                value={selectedProductId}
                onChange={(e) => {
                  setSelectedProductId(e.target.value)
                  setHistoryProductId(e.target.value ? Number(e.target.value) : null)
                }}
              >
                <option value="">Chọn loại gạo</option>
                {products.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} (tồn: {Number(p.stock_kg ?? 0).toFixed(1)} kg)
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              <span>Số kg nhập</span>
              <input
                type="number"
                min="0.1"
                step="0.1"
                value={qtyKg}
                onChange={(e) => setQtyKg(e.target.value)}
                placeholder="100"
              />
            </label>
            <label className="field">
              <span>Giá vốn lần này (đ/kg)</span>
              <input
                type="number"
                min="0"
                step="100"
                value={costPerKg}
                onChange={(e) => setCostPerKg(e.target.value)}
                placeholder="12000"
              />
            </label>
            {selectedProduct && qtyKg && costPerKg && (
              <div className="import-preview">
                <div className="preview-row">
                  <span>Tồn hiện tại</span>
                  <strong>{Number(selectedProduct.stock_kg ?? 0).toFixed(1)} kg</strong>
                </div>
                <div className="preview-row">
                  <span>Giá vốn hiện tại</span>
                  <strong>{formatMoney(selectedProduct.cost_per_kg)}/kg</strong>
                </div>
                <div className="preview-row accent">
                  <span>Tồn sau nhập</span>
                  <strong>
                    {(Number(selectedProduct.stock_kg ?? 0) + Number(qtyKg)).toFixed(1)} kg
                  </strong>
                </div>
                {previewNewCost !== null && (
                  <div className="preview-row accent">
                    <span>Giá vốn bình quân mới</span>
                    <strong>{formatMoney(previewNewCost)}/kg</strong>
                  </div>
                )}
              </div>
            )}
            <MutationError error={importMutation.error} />
            <button
              className="button primary"
              type="button"
              disabled={!selectedProductId || !qtyKg || !costPerKg || importMutation.isPending}
              onClick={() => importMutation.mutate()}
            >
              {importMutation.isPending ? (
                <Archive size={18} />
              ) : (
                <Archive size={18} />
              )}{' '}
              Nhập kho
            </button>
          </div>
        </section>
        <section className="panel">
          <PanelTitle title="Tồn kho hiện tại" icon={<Warehouse size={18} />} />
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Loại gạo</th>
                  <th>Tồn (kg)</th>
                  <th>Giá vốn</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {products.map((p) => (
                  <tr key={p.id} className={!p.active ? 'row-muted' : ''}>
                    <td><strong>{p.name}</strong></td>
                    <td>
                      <span className={Number(p.stock_kg ?? 0) <= 0 ? 'pill pill-muted' : 'pill pill-green'}>
                        {Number(p.stock_kg ?? 0).toFixed(1)} kg
                      </span>
                    </td>
                    <td>{formatMoney(p.cost_per_kg)}</td>
                    <td>
                      <button
                        className="button ghost"
                        type="button"
                        onClick={() =>
                          setHistoryProductId(p.id === historyProductId ? null : p.id)
                        }
                      >
                        Lịch sử
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {historyProductId !== null && (
            <div className="history-panel">
              <h3>Lịch sử nhập - {products.find((p) => p.id === historyProductId)?.name}</h3>
              <DataState isLoading={historyQuery.isLoading} error={historyQuery.error} />
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Ngày nhập</th>
                      <th>Số kg</th>
                      <th>Giá vốn</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(historyQuery.data ?? []).map((entry: StockEntry) => (
                      <tr key={entry.id}>
                        <td>{new Date(entry.imported_at).toLocaleString('vi-VN')}</td>
                        <td>{Number(entry.quantity_kg).toFixed(1)} kg</td>
                        <td>{formatMoney(entry.cost_per_kg)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {(historyQuery.data ?? []).length === 0 && !historyQuery.isLoading && (
                <div className="empty-state">Chưa có lịch sử nhập kho.</div>
              )}
            </div>
          )}
        </section>
      </div>
    </section>
  )
}
