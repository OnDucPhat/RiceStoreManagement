import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Loader2, CheckCircle2 } from 'lucide-react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api, type RiceProduct, type ProductInput } from '../../../api'
import { Modal } from '../../shared/Modal'
import { TextField } from '../../shared/form/TextField'
import { TextArea } from '../../shared/form/TextArea'
import { MutationError } from '../../shared/MutationError'
import { fieldError } from '../../../utils'

const productSchema = z.object({
  name: z.string().min(1, 'Nhập tên gạo'),
  characteristics: z.string().min(1, 'Nhập đặc tính'),
  price_per_kg: z.coerce.number().min(0, 'Giá bán không âm'),
  cost_per_kg: z.coerce.number().min(0, 'Giá vốn không âm'),
  active: z.boolean(),
})

type ProductFormInput = z.input<typeof productSchema>
type ProductFormValues = z.output<typeof productSchema>

export function ProductModal({ product, onClose }: { product?: RiceProduct; onClose: () => void }) {
  const queryClient = useQueryClient()
  const form = useForm<ProductFormInput, unknown, ProductFormValues>({
    resolver: zodResolver(productSchema),
    defaultValues: product
      ? {
          name: product.name,
          characteristics: product.characteristics,
          price_per_kg: product.price_per_kg,
          cost_per_kg: product.cost_per_kg,
          active: product.active,
        }
      : {
          name: '',
          characteristics: '',
          price_per_kg: 0,
          cost_per_kg: 0,
          active: true,
        },
  })
  const mutation = useMutation({
    mutationFn: (values: ProductInput) =>
      product ? api.updateProduct(product.id, values) : api.createProduct(values),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['products'] })
      onClose()
    },
  })

  return (
    <Modal title={product ? 'Sửa loại gạo' : 'Thêm loại gạo'} onClose={onClose}>
      <form
        className="form-grid"
        onSubmit={form.handleSubmit((values) => mutation.mutate(values))}
      >
        <TextField
          label="Tên gạo"
          registration={form.register('name')}
          error={fieldError(form.formState.errors.name?.message)}
        />
        <TextArea
          label="Đặc tính"
          registration={form.register('characteristics')}
          error={fieldError(form.formState.errors.characteristics?.message)}
        />
        <TextField
          label="Giá bán/kg"
          type="number"
          registration={form.register('price_per_kg')}
          error={fieldError(form.formState.errors.price_per_kg?.message)}
        />
        <TextField
          label="Giá vốn/kg"
          type="number"
          registration={form.register('cost_per_kg')}
          error={fieldError(form.formState.errors.cost_per_kg?.message)}
        />
        <label className="toggle-row">
          <input type="checkbox" {...form.register('active')} />
          Đang bán
        </label>
        <MutationError error={mutation.error} />
        <div className="modal-actions">
          <button className="button ghost" type="button" onClick={onClose}>
            Hủy
          </button>
          <button
            className="button primary"
            type="submit"
            disabled={mutation.isPending}
          >
            {mutation.isPending ? (
              <Loader2 className="spin" size={18} />
            ) : (
              <CheckCircle2 size={18} />
            )}{' '}
            Lưu
          </button>
        </div>
      </form>
    </Modal>
  )
}
