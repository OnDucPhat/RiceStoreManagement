import { useState, useMemo } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Loader2, CheckCircle2 } from 'lucide-react'
import { api, type RiceProduct } from '../../../api'
import { Modal } from '../../shared/Modal'
import { SimpleField } from '../../shared/form/SimpleField'
import { DataState } from '../../shared/DataState'
import { MutationError } from '../../shared/MutationError'
import { formatMoney } from '../../../utils'

type RetailOrderLine = {
  productId: number
  productName: string
  pricePerKg: number
  quantityKg: number
  lineTotal: number
}

export function CreateRetailOrderModal({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient()
  const productsQuery = useQuery({ queryKey: ['products', true], queryFn: () => api.products(true) })
  const [lines, setLines] = useState<RetailOrderLine[]>([])
  const [customerName, setCustomerName] = useState('')
  const [customerPhone, setCustomerPhone] = useState('')
  const [loyaltyPhone, setLoyaltyPhone] = useState('')
  const [quantityInputs, setQuantityInputs] = useState<Record<number, string>>({})
  const [errors, setErrors] = useState<Record<string, string>>({})

  const totalPrice = useMemo(() => lines.reduce((sum, l) => sum + l.lineTotal, 0), [lines])

  function addProduct(product: RiceProduct) {
    const qty = parseFloat(quantityInputs[product.id] || '0')
    if (!qty || qty <= 0) return
    setLines((prev) => {
      const existing = prev.find((l) => l.productId === product.id)
      if (existing) {
        const newQty = existing.quantityKg + qty
        return prev.map((l) =>
          l.productId === product.id
            ? { ...l, quantityKg: newQty, lineTotal: newQty * l.pricePerKg }
            : l
        )
      }
      return [
        ...prev,
        {
          productId: product.id,
          productName: product.name,
          pricePerKg: product.price_per_kg,
          quantityKg: qty,
          lineTotal: qty * product.price_per_kg,
        },
      ]
    })
    setQuantityInputs((prev) => ({ ...prev, [product.id]: '' }))
  }

  function removeLine(productId: number) {
    setLines((prev) => prev.filter((l) => l.productId !== productId))
  }

  function validate(): boolean {
    const errs: Record<string, string> = {}
    if (!customerName.trim()) errs.customerName = 'Nhập tên khách'
    if (lines.length === 0) errs.lines = 'Chọn ít nhất 1 sản phẩm'
    setErrors(errs)
    return Object.keys(errs).length === 0
  }

  const mutation = useMutation({
    mutationFn: () =>
      api.createRetailOrder({
        customer_name: customerName.trim(),
        customer_phone: customerPhone.trim() || undefined,
        loyalty_phone: loyaltyPhone.trim() || undefined,
        items: lines.map((l) => ({ product_id: l.productId, quantity_kg: l.quantityKg })),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['orders'] })
      queryClient.invalidateQueries({ queryKey: ['products'] })
      onClose()
    },
  })

  return (
    <Modal title="Tạo đơn tại quầy" onClose={onClose}>
      <div className="retail-order-form">
        <div className="retail-product-picker">
          <h4>Chọn sản phẩm</h4>
          <DataState isLoading={productsQuery.isLoading} error={productsQuery.error} />
          <div className="retail-product-list">
            {(productsQuery.data ?? []).map((product) => (
              <div key={product.id} className="retail-product-row">
                <div className="retail-product-info">
                  <strong>{product.name}</strong>
                  <span className="retail-price">{formatMoney(product.price_per_kg)}/kg</span>
                  <span className="retail-stock">Còn {Number(product.stock_kg ?? 0).toFixed(1)} kg</span>
                </div>
                <div className="retail-product-add">
                  <input
                    type="number"
                    min="0.1"
                    step="0.1"
                    placeholder="kg"
                    value={quantityInputs[product.id] ?? ''}
                    onChange={(e) =>
                      setQuantityInputs((prev) => ({ ...prev, [product.id]: e.target.value }))
                    }
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault()
                        addProduct(product)
                      }
                    }}
                  />
                  <button
                    className="button primary small"
                    type="button"
                    onClick={() => addProduct(product)}
                  >
                    +
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="retail-order-lines">
          <h4>Đơn hàng</h4>
          {errors.lines ? <p className="field-error">{errors.lines}</p> : null}
          {lines.length === 0 ? (
            <p className="empty-hint">Chưa chọn sản phẩm nào.</p>
          ) : (
            <table className="retail-lines-table">
              <thead>
                <tr>
                  <th>Sản phẩm</th>
                  <th>Đơn giá</th>
                  <th>Khối lượng</th>
                  <th>Thành tiền</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {lines.map((line) => (
                  <tr key={line.productId}>
                    <td>{line.productName}</td>
                    <td>{formatMoney(line.pricePerKg)}</td>
                    <td>{line.quantityKg.toFixed(1)} kg</td>
                    <td>{formatMoney(line.lineTotal)}</td>
                    <td>
                      <button
                        className="button ghost small danger"
                        type="button"
                        onClick={() => removeLine(line.productId)}
                      >
                        x
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={3}><strong>Tổng cộng</strong></td>
                  <td colSpan={2}><strong className="retail-total">{formatMoney(totalPrice)}</strong></td>
                </tr>
              </tfoot>
            </table>
          )}
        </div>

        <div className="retail-customer-info">
          <h4>Thông tin khách hàng</h4>
          <div className="form-grid">
            <SimpleField
              label="Tên khách *"
              value={customerName}
              onChange={setCustomerName}
              error={errors.customerName}
            />
            <SimpleField
              label="SĐT"
              value={customerPhone}
              onChange={setCustomerPhone}
            />
            <SimpleField
              label="SĐT tích điểm"
              value={loyaltyPhone}
              onChange={setLoyaltyPhone}
              placeholder="Bỏ trống = dùng SĐT trên"
            />
          </div>
        </div>

        <MutationError error={mutation.error} />
        <div className="modal-actions">
          <button className="button ghost" type="button" onClick={onClose}>
            Hủy
          </button>
          <button
            className="button success"
            type="button"
            disabled={mutation.isPending}
            onClick={() => {
              if (validate()) mutation.mutate()
            }}
          >
            {mutation.isPending ? (
              <Loader2 className="spin" size={18} />
            ) : (
              <CheckCircle2 size={18} />
            )}{' '}
            Hoàn tất thanh toán
          </button>
        </div>
      </div>
    </Modal>
  )
}
